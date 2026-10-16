package com.example.aiagent.agent;

import cn.hutool.core.util.StrUtil;
import com.example.aiagent.agent.model.AgentState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
        if (state != AgentState.IDLE) {
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

    public SseEmitter runStream(String userPrompt) {
        SseEmitter sseEmitter = new SseEmitter();

        // 开启异步执行
        CompletableFuture.runAsync(() -> {
            try {
                if (state != AgentState.IDLE) {
                    sseEmitter.send("错误：无法从状态运行代理: " + this.state);
                    sseEmitter.complete();
                    return;
                }
                if (StrUtil.isBlank(userPrompt)) {
                    sseEmitter.send("错误：不能使用空提示词");
                    sseEmitter.complete();
                    return;
                }
            } catch (Exception e) {
                sseEmitter.completeWithError(e);
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
                    // 每步结果通过sse输出
                    sseEmitter.send(result);
                }

                if (currentStep >= maxSteps) {
                    state = AgentState.FINISHED;
                    results.add("Terminated: Reached max steps (" + maxSteps + ")");
                    sseEmitter.send("执行结束：达到最大步骤（" + maxSteps + "）");
                }

                sseEmitter.complete();
            } catch (Exception e) {
                state = AgentState.ERROR;
                log.error("Error executing agent", e);
                try {
                    sseEmitter.send("执行错误：" + e.getMessage());
                    sseEmitter.complete();
                } catch (IOException ex) {
                    sseEmitter.completeWithError(ex);
                }
            } finally {
                cleanup();
            }
        });

        // 设置超时和完成回调
        sseEmitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            this.cleanup();
            log.warn("SSE connection timed out");
        });

        sseEmitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            this.cleanup();
            log.info("SSE connection completed");
        });

        return sseEmitter;
    }

    public abstract String step();

    protected void cleanup() {
        // 清理资源
    }
}
