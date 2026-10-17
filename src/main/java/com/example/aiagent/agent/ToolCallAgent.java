package com.example.aiagent.agent;

import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.example.aiagent.agent.model.AgentEvent;
import com.example.aiagent.agent.model.AgentState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** 工具结果用于模型推理，界面只接收可读进度和最终答复。 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class ToolCallAgent extends ReActAgent {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ToolCallback[] availableTools;
    private final ToolCallingManager toolCallingManager;
    private final ChatOptions chatOptions;
    private final AtomicInteger toolSequence = new AtomicInteger();
    private ChatResponse toolCallChatResponse;

    public ToolCallAgent(ToolCallback[] availableTools) {
        this.availableTools = Arrays.stream(availableTools).map(this::observeTool).toArray(ToolCallback[]::new);
        this.toolCallingManager = ToolCallingManager.builder().build();
        this.chatOptions = DashScopeChatOptions.builder()
                .toolCallbacks(List.of(this.availableTools))
                .internalToolExecutionEnabled(false)
                .build();
    }

    @Override
    public boolean thinking() {
        checkCancellation();
        publishEvent(AgentEvent.progress("planning", "正在分析任务与已有资料", "running"));
        ChatResponse response = getChatClient().prompt(new Prompt(getMessageList(), chatOptions))
                .system(getSystemPrompt() + "\n" + getNextStepPrompt())
                .call()
                .chatResponse();
        checkCancellation();
        if (response == null || response.getResult() == null) {
            throw new IllegalStateException("模型未返回有效结果");
        }
        toolCallChatResponse = response;
        AssistantMessage assistant = response.getResult().getOutput();
        publishEvent(AgentEvent.progress("planning", "已完成任务分析", "completed"));

        if (!assistant.hasToolCalls()) {
            if (StrUtil.isBlank(assistant.getText())) {
                throw new IllegalStateException("模型未返回有效答复");
            }
            getMessageList().add(assistant);
            setFinalAnswer(assistant.getText());
            setState(AgentState.FINISHED);
            return false;
        }
        return true;
    }

    @Override
    public String act() {
        checkCancellation();
        ToolExecutionResult result = toolCallingManager.executeToolCalls(
                new Prompt(getMessageList(), chatOptions), toolCallChatResponse);
        checkCancellation();
        setMessageList(new ArrayList<>(result.conversationHistory()));
        ToolResponseMessage responses = (ToolResponseMessage) getMessageList().getLast();
        if (responses.getResponses().stream().anyMatch(response -> "doTerminate".equals(response.name()))) {
            setState(AgentState.FINISHED);
        }
        // 原始工具数据已留在 conversationHistory 中，不作为聊天正文返回。
        return "工具操作已完成";
    }

    @Override
    protected String buildFinalAnswer() {
        if (StrUtil.isNotBlank(getFinalAnswer())) {
            return getFinalAnswer();
        }
        List<Message> messages = new ArrayList<>(getMessageList());
        String limitNotice = getState() == AgentState.RUNNING && getCurrentStep() >= getMaxSteps()
                ? "本次已达到工具执行次数上限，必须明确说明哪些部分尚未完成。" : "";
        messages.add(new UserMessage("""
                现在停止调用工具，根据用户原始需求和已有工具结果，直接撰写最终答复。
                使用用户的语言和清晰的 Markdown，先给结论，再给有用的细节、建议与来源链接。
                不输出 Step、工具调用日志、原始 JSON 或内部推理过程。不要把工具操作完成当作答复。
                如生成文件，只说明真实生成的文件名，下载入口由界面提供；不输出服务器本地路径或编造链接。
                诚实说明缺失资料、执行失败和未完成事项，不得声称完成未执行成功的操作。
                """ + limitNotice));

        StringBuilder answer = new StringBuilder();
        // 汇总阶段不提供工具，避免终止工具或次数上限之后再次启动操作。
        getChatClient().prompt(new Prompt(messages))
                .system(getSystemPrompt())
                .options(DashScopeChatOptions.builder().toolCallbacks(List.of())
                        .internalToolExecutionEnabled(false).build())
                .stream().content().toIterable().forEach(chunk -> {
                    checkCancellation();
                    answer.append(chunk);
                    publishEvent(AgentEvent.delta(chunk));
                });
        return answer.toString();
    }

    private ToolCallback observeTool(ToolCallback delegate) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return delegate.getToolDefinition();
            }

            @Override
            public ToolMetadata getToolMetadata() {
                return delegate.getToolMetadata();
            }

            @Override
            public String call(String input) {
                return invoke(input, null);
            }

            @Override
            public String call(String input, ToolContext context) {
                return invoke(input, context);
            }

            private String invoke(String input, ToolContext context) {
                checkCancellation();
                String name = getToolDefinition().name();
                String id = "tool-" + toolSequence.incrementAndGet();
                String title = toolTitle(name);
                publishEvent(AgentEvent.progress(id, title, "running"));
                try {
                    String result = context == null ? delegate.call(input) : delegate.call(input, context);
                    checkCancellation();
                    String text = unwrapResult(result);
                    boolean failed = text.startsWith("Error ") || text.startsWith("Error:");
                    publishEvent(AgentEvent.progress(id, title, failed ? "failed" : "completed"));
                    if ("generatePDF".equals(name) && !failed) {
                        AgentArtifacts.fromPdfResult(text).ifPresent(ToolCallAgent.this::publishEvent);
                    }
                    return result;
                } catch (RuntimeException e) {
                    checkCancellation();
                    publishEvent(AgentEvent.progress(id, title, "failed"));
                    throw e;
                }
            }
        };
    }

    private String unwrapResult(String result) {
        if (result != null && result.startsWith("\"")) {
            try {
                return JSON.readValue(result, String.class);
            } catch (JsonProcessingException ignored) {
                // 非 JSON 字符串按工具原文处理。
            }
        }
        return result == null ? "" : result;
    }

    private String toolTitle(String name) {
        return switch (name) {
            case "searchWeb" -> "检索相关资料";
            case "scrapeWebPage" -> "阅读网页资料";
            case "downloadResource" -> "获取所需素材";
            case "generatePDF" -> "生成 PDF 文档";
            case "writeFile" -> "保存整理结果";
            case "readFile" -> "读取参考文件";
            case "executeTerminalCommand" -> "处理文件与数据";
            case "doTerminate" -> "结束工具操作";
            default -> "执行辅助操作";
        };
    }
}
