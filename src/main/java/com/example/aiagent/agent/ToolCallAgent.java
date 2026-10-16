package com.example.aiagent.agent;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.example.aiagent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 处理工具调用基础类，具体实现了 thinking 和 Act 方法
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class ToolCallAgent extends ReActAgent {

    // 可用工具
    private final ToolCallback[] availableTools;
    // 调用工具
    private final ToolCallingManager toolCallingManager;

    private final ChatOptions chatOptions;

    private ChatResponse toolCallChatResponse;

    public ToolCallAgent(ToolCallback[] availableTools) {
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        this.chatOptions = DashScopeChatOptions.builder()
                .internalToolExecutionEnabled(false)
                .build();
    }


    @Override
    public boolean thinking() {
        if (getNextStepPrompt() != null && !getNextStepPrompt().isEmpty()) {
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessageList().add(userMessage);
        }
        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, chatOptions);
        try {
            // 执行工具
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .toolCallbacks(availableTools)
                    .call()
                    .chatResponse();
            this.toolCallChatResponse = chatResponse;
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            // 输出提示信息
            String result = assistantMessage.getText();
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
            log.info(getName() + "的思考: " + result);
            log.info(getName() + "选择了 " + toolCallList.size() + " 个工具来使用");
            String toolCallInfo = toolCallList.stream()
                    .map(toolCall -> String.format("工具名称：%s，参数：%s",
                            toolCall.name(),
                            toolCall.arguments())
                    )
                    .collect(Collectors.joining("\n"));
            log.info(toolCallInfo);

            if (toolCallList.isEmpty()) {
                // 只有不调用工具时，才记录助手消息
                getMessageList().add(assistantMessage);
                return false;
            } else {
                // 需要调用工具时，无需记录助手消息，因为调用工具时会自动记录
                return true;
            }
        } catch (Exception e) {
            log.error(getName() + "的思考过程遇到了问题: " + e.getMessage());
            getMessageList().add(
                    new AssistantMessage("处理时遇到错误: " + e.getMessage()));
            return false;
        }
    }

    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return "无需调用工具";
        }
        Prompt prompt = new Prompt(getMessageList(), chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
        setMessageList(toolExecutionResult.conversationHistory());
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) toolExecutionResult.conversationHistory().getLast();
        // 当前工具调用的结果
        String results = toolResponseMessage.getResponses().stream()
                .map(response -> "工具 " + response.name() + " 完成了它的任务！结果: " + response.responseData())
                .collect(Collectors.joining("\n"));

        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(toolResponse -> "doTerminate".equals(toolResponse.name()));
        if (terminateToolCalled) {
            setState(AgentState.FINISHED);
        }
        log.info(results);
        return results;
    }
}
