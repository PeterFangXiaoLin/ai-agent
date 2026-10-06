package com.example.aiagent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class PgVectorVectorStoreConfig {

    @Resource
    private LoveAppDocumentLoader loveAppDocumentLoader;

    @Resource
    PgVectorStore vectorStore;

    @Bean
    public VectorStore pgVectorVectorStore() {
        // 加载文档
        List<Document> documents = loveAppDocumentLoader.loadDocuments();
        vectorStore.doAdd(documents);
        return vectorStore;
    }
}
