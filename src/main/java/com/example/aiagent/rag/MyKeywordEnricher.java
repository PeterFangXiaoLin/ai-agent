package com.example.aiagent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MyKeywordEnricher {

    @Resource
    private ChatModel dashScopeChatModel;

    public List<Document> enrichDocuments(List<Document> documents) {
        KeywordMetadataEnricher keywordMetadataEnricher = KeywordMetadataEnricher.builder(dashScopeChatModel)
                .keywordCount(5)
                .build();

        return keywordMetadataEnricher.apply(documents);
    }
}
