package com.urooz.naaz.controller;

import com.urooz.naaz.service.DataIngestionService;
import com.urooz.naaz.service.PharmacySearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/pharmacy")
public class PharmacyController {

    private static final Logger logger = LoggerFactory.getLogger(PharmacyController.class);
    private final PharmacySearchService searchService;
    private final DataIngestionService ingestionService;

    public PharmacyController(PharmacySearchService searchService, DataIngestionService ingestionService) {
        this.searchService = searchService;
        this.ingestionService = ingestionService;
    }

    @GetMapping("/search")
    public Map<String, String> searchMedicine(
            @RequestParam String query,
            @RequestParam(required = false) String chatId) {

        if (chatId == null || chatId.trim().isEmpty()) {
            chatId = "user-" + UUID.randomUUID().toString().substring(0, 8);
            logger.info("No ChatID provided. Generated new session ID: {}", chatId);
        }

        logger.info("Received API request. Query: [{}] | ChatID: [{}]", query, chatId);

        String answer = searchService.searchMedicine(query, chatId);

        return Map.of(
                "answer", answer,
                "chatId", chatId
        );
    }

    @PostMapping("/refresh")
    public Map<String, String> refreshData() {
        logger.info("Received manual data refresh request.");
        String status = ingestionService.refreshData();
        return Map.of("status", status);
    }
}