package com.example.aiagent.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 恋爱大师文档加载器
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LoveAppDocumentLoader {

    private final ResourcePatternResolver resourcePatternResolver;

    public List<Document> loadDocuments() {
        List<Document> documents = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                String status = filename.substring(filename.length() - 6, filename.length() - 4);
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        // 遇到 Markdown 水平分割线（如 ---）时，将分割线前后的内容拆成不同的 Document
                        .withHorizontalRuleCreateDocument(true)
                        // 不把 Markdown 代码块（如 ```java ... ```）的内容加入 Document
                        .withIncludeCodeBlock(false)
                        // 不把 Markdown 引用块（以 > 开头的内容）加入 Document
                        .withIncludeBlockquote(false)
                        .withAdditionalMetadata(Map.of("filename", filename, "status", status))
                        .build();

                MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);
                documents.addAll(reader.get());
            }
        } catch (IOException e) {
            log.error("Markdown 文档加载失败", e);
        }
        return documents;
    }
}
