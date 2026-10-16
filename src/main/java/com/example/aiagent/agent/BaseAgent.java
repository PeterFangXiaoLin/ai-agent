package com.example.aiagent.agent;

import cn.hutool.core.util.StrUtil;
import com.example.aiagent.agent.model.AgentState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Data
public abstract class BaseAgent {

    // agent name
    private String name;

    // 状态
    private AgentState state = AgentState.IDLE;

    // 模型
    private ChatClient chatClient;

    // 最大迭代次数
    private int maxSteps = 10;
    private int currentStep;

    // 记忆
    private List<Message> messageList = new ArrayList<>();

    // 系统提示词
    private String systemPrompt;
    private String nextStepPrompt;

    public String run(String userPrompt) {
        if (state !=  AgentState.IDLE) {
            throw new RuntimeException("Cannot run agent from state: " + this.state);
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new RuntimeException("Cannot run agent with empty user prompt");
        }

        state = AgentState.RUNNING;
        messageList.add(new UserMessage(userPrompt));

        List<String> results = new ArrayList<>();

        try {
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                int stepNumber = i + 1;
                currentStep = stepNumber;
                log.info("Executing step " + stepNumber + "/" + maxSteps);
                String stepResult = step();
                String result = "Step " + stepNumber + ": " + stepResult;
                results.add(result);
            }

            if (currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                results.add("Terminated: Reached max steps (" + maxSteps + ")");
            }

            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("Error executing agent", e);
            return "执行错误" + e.getMessage();
        } finally {
            cleanup();
        }
    }

    public abstract String step();

    protected void cleanup() {
        // 清理资源
    }
}
