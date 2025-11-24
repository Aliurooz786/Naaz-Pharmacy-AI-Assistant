package com.urooz.naaz.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

@Service
public class PharmacySearchService {

    private static final Logger logger = LoggerFactory.getLogger(PharmacySearchService.class);

    private final SimpleVectorStore vectorStore;
    private final ChatClient chatClient;

    @Value("classpath:prompts/system-prompt.txt")
    private Resource systemPromptResource;

    public PharmacySearchService(SimpleVectorStore vectorStore, ChatClient.Builder chatClientBuilder, org.springframework.ai.chat.memory.ChatMemory chatMemory) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder
                .defaultAdvisors(new org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor(chatMemory))
                .build();
    }

    public String searchMedicine(String userQuery, String chatId) {
        logger.info("Processing Search | Query: [{}] | ChatID: [{}]", userQuery, chatId);

        String searchFriendlyQuery = rewriteQueryIfNeeded(userQuery, chatId);

        List<Document> similarDocuments = vectorStore.similaritySearch(
                SearchRequest.query(searchFriendlyQuery).withTopK(15)
        );

        if (similarDocuments.isEmpty()) {
            return "Maaf kijiye, mere paas is dawa ya query se juda data uplabdh nahi hai.";
        }

        String context = similarDocuments.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n---\n"));

        PromptTemplate promptTemplate = new PromptTemplate(systemPromptResource);
        String finalPrompt = promptTemplate.render(Map.of(
                "context", context,
                "query", userQuery
        ));

        try {
            String rawResponse = chatClient.prompt()
                    .user(finalPrompt)
                    .advisors(a -> a
                            .param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                    .call()
                    .content();


            return cleanAIResponse(rawResponse);

        } catch (Exception e) {
            logger.error("AI Error: {}", e.getMessage());
            return "Server error. Please try again.";
        }
    }

    /**
     * Ye method 'Thinking Process' ko hata kar sirf 'Final Answer' return karta hai.
     */
    private String cleanAIResponse(String response) {
        if (response == null) return "";

        if (response.contains("Final Answer:")) {
            return response.substring(response.lastIndexOf("Final Answer:") + 13).trim();
        }

        if (response.contains("Thinking Process:")) {
            return response.replaceAll("Thinking Process:[\\s\\S]*?(Answer:|Jawab:)", "").trim();
        }

        return response;
    }

    private String rewriteQueryIfNeeded(String originalQuery, String chatId) {
        String rewritePrompt = String.format("""
            Rewriter Task:
            User query ko complete karo based on history.
            Example: History="Price?", Last="Dolo" -> Output="Dolo Price?"
            Agar query clear hai toh same return karo.
            Current Query: "%s"
            Output ONLY the rewritten query.
            """, originalQuery);

        try {
            return chatClient.prompt().user(rewritePrompt)
                    .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId).param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                    .call().content();
        } catch (Exception e) {
            return originalQuery;
        }
    }
}