package com.example.aiagent.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.stereotype.Component;

/**
 * 查询重写
 */
@Component
public class QueryRewriter {

    private final QueryTransformer queryTransformer;

    public QueryRewriter(ChatModel dashScopeChatModel) {
        this.queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(ChatClient.builder(dashScopeChatModel))
                .build();
    }

    public String doQueryRewrite(String prompt) {
        return queryTransformer.transform(new Query(prompt)).text();
    }
}
