package com.urooz.naaz.service;

import com.urooz.naaz.model.Medicine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DataIngestionService implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataIngestionService.class);
    private final SimpleVectorStore vectorStore;
    private final ResourceLoader resourceLoader;

    @Value("${app.medicines.sheet-url}")
    private String sheetUrl;

    public DataIngestionService(SimpleVectorStore vectorStore, ResourceLoader resourceLoader) {
        this.vectorStore = vectorStore;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void run(String... args) {
        refreshData();
    }

    public String refreshData() {
        try {
            logger.info("Syncing with Live Google Sheet...");

            Resource resource = resourceLoader.getResource(sheetUrl);
            if (!resource.exists()) {
                return "Error: Google Sheet URL kaam nahi kar raha.";
            }

            List<Document> documents = new ArrayList<>();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                boolean isHeader = true;

                while ((line = reader.readLine()) != null) {
                    if (isHeader) { isHeader = false; continue; }

                    String[] data = line.split(",");
                    if (data.length < 8) continue;

                    Medicine med = new Medicine(
                            data[0].trim(), data[1].trim(), data[2].trim(), data[3].trim(),
                            data[4].trim(), data[5].trim(), data[6].trim(), data[7].trim()
                    );

                    String content = String.format("""
                        Medicine Name: %s
                        Generic Name: %s
                        Location: %s
                        Stock Available: %s units
                        Price: %s
                        Expiry Date: %s
                        Usage: %s
                        """, med.medicineName(), med.genericName(), med.rackLocation(),
                            med.stockQuantity(), med.price(), med.expiryDate(), med.usageDescription());

                    Document doc = new Document(med.itemId(), content, Map.of(
                            "medicine_name", med.medicineName(),
                            "rack_location", med.rackLocation()
                    ));

                    documents.add(doc);
                }
            }

            if (!documents.isEmpty()) {
                vectorStore.add(documents);
                logger.info("Data Refresh Successful! {} items loaded.", documents.size());
                return "Success: Live Data Refreshed! (" + documents.size() + " items)";
            } else {
                return "Warning: Sheet khali hai.";
            }

        } catch (Exception e) {
            logger.error("Refresh Failed", e);
            return "Update Failed: " + e.getMessage();
        }
    }
}