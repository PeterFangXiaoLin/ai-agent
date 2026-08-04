package com.example.aiagent.demo.invoke;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 参考 https://java2ai.com/docs/frameworks/agent-framework/tutorials/models
 */
@Component
public class SpringAiAiInvoke implements CommandLineRunner {

    @Resource
    private ChatModel dashscopeChatModel;

    @Override
    public void run(String... args) throws Exception {
        ChatResponse response = dashscopeChatModel.call(new Prompt("解释什么是微服务架构"));
        String answer = response.getResult().getOutput().getText();
        System.out.println(answer);
    }
}
