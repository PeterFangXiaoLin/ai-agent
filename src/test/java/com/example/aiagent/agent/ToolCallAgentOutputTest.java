package com.example.aiagent.agent;

import com.example.aiagent.agent.model.AgentEvent;
import com.example.aiagent.agent.model.AgentState;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import reactor.core.publisher.Flux;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallAgentOutputTest {

    private static final String RAW_RESULT = "{\"position\":1,\"title\":\"网页原始结果\",\"link\":\"https://example.com\"}";

    @Test
    void noToolResponseBecomesTheFinalAnswerAndEndsImmediately() {
        ScriptedModel model = new ScriptedModel(List.of(response("这是最终答复")));
        RecordingAgent agent = new RecordingAgent(model);

        assertEquals("这是最终答复", agent.run("帮我解答问题"));
        assertEquals(1, model.calls.size());
        assertEquals(AgentState.FINISHED, agent.getState());
        assertEquals(1, agent.events.stream().filter(event -> event.type().equals("answer")).count());
    }

    @Test
    void toolJsonStaysInModelContextWhileTheUserReceivesTheAnswer() {
        ScriptedModel model = new ScriptedModel(List.of(toolCall("searchWeb"), response("## 推荐方案\n根据资料整理的答复")));
        RecordingAgent agent = new RecordingAgent(model, tool("searchWeb", RAW_RESULT));

        String answer = agent.run("给我一份推荐方案");

        assertEquals("## 推荐方案\n根据资料整理的答复", answer);
        assertEquals(2, model.calls.size());
        assertEquals(1, ((ToolCallingChatOptions) model.calls.getFirst().getOptions()).getToolCallbacks().size());
        assertTrue(agent.getMessageList().stream().filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast)
                .anyMatch(message -> message.getResponses().getFirst().responseData().equals(RAW_RESULT)));
        assertFalse(agent.events.toString().contains("position"));
        assertFalse(answer.contains("Step"));
        assertEquals(List.of("running", "completed"), agent.events.stream()
                .filter(event -> "tool-1".equals(event.id())).map(AgentEvent::status).toList());
    }

    @Test
    void terminationStillGeneratesAFinalAnswerWithoutEnablingMoreTools() {
        ScriptedModel model = new ScriptedModel(List.of(toolCall("doTerminate")));
        RecordingAgent agent = new RecordingAgent(model, tool("doTerminate", "任务结束"));

        assertEquals("已整理的完整答复", agent.run("完成任务后给我答复"));
        assertEquals(1, model.summaries.size());
        assertTrue(((ToolCallingChatOptions) model.summaries.getFirst().getOptions()).getToolCallbacks().isEmpty());
        assertTrue(agent.events.stream().anyMatch(event -> event.type().equals("delta")));
    }

    @Test
    void stepLimitSummarizesKnownResultsAndExplicitlyMarksUnfinishedWork() {
        ScriptedModel model = new ScriptedModel(List.of(toolCall("searchWeb")));
        RecordingAgent agent = new RecordingAgent(model, tool("searchWeb", RAW_RESULT));
        agent.setMaxSteps(1);

        assertEquals("已整理的完整答复", agent.run("整理资料"));
        assertTrue(model.summaries.getFirst().getInstructions().getLast().getText().contains("尚未完成"));
        assertEquals(1, model.calls.size());
    }

    @Test
    void failedToolsAreMarkedAndCanBeExplainedByTheFinalAnswer() {
        ScriptedModel model = new ScriptedModel(List.of(toolCall("searchWeb"), response("检索失败，暂时无法确认资料。")));
        RecordingAgent agent = new RecordingAgent(model, tool("searchWeb", "Error searching Baidu: timeout"));

        assertEquals("检索失败，暂时无法确认资料。", agent.run("查询资料"));
        assertTrue(agent.events.stream().anyMatch(event -> "tool-1".equals(event.id()) && "failed".equals(event.status())));
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static ChatResponse toolCall(String name) {
        AssistantMessage assistant = AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", name, "{}"))).build();
        return new ChatResponse(List.of(new Generation(assistant)));
    }

    private static ToolCallback tool(String name, String result) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name(name).description("测试工具")
                        .inputSchema("{\"type\":\"object\",\"properties\":{}}").build();
            }

            @Override
            public String call(String input) {
                return result;
            }
        };
    }

    private static final class ScriptedModel implements ChatModel {
        private final Queue<ChatResponse> responses;
        private final List<Prompt> calls = new ArrayList<>();
        private final List<Prompt> summaries = new ArrayList<>();

        private ScriptedModel(List<ChatResponse> responses) {
            this.responses = new ArrayDeque<>(responses);
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            calls.add(prompt);
            return responses.remove();
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            summaries.add(prompt);
            return Flux.just(response("已整理的"), response("完整答复"));
        }
    }

    private static final class RecordingAgent extends ToolCallAgent {
        private final List<AgentEvent> events = new ArrayList<>();

        private RecordingAgent(ChatModel model, ToolCallback... tools) {
            super(tools);
            setChatClient(ChatClient.create(model));
            setSystemPrompt("整理用户需要的答复");
            setNextStepPrompt("必要时使用工具");
        }

        @Override
        protected void publishEvent(AgentEvent event) {
            super.publishEvent(event);
            events.add(event);
        }
    }
}
