package com.example.aiagent.agent;

import cn.hutool.core.util.StrUtil;
import com.example.aiagent.agent.model.AgentEvent;
import com.example.aiagent.agent.model.AgentState;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CancellationException;
import java.util.function.Predicate;

@Slf4j
@Data
public abstract class BaseAgent {

    private static final long STREAM_TIMEOUT_MILLIS = 600_000L;
    private static final long HEARTBEAT_INTERVAL_MILLIS = 15_000L;

    // agent name
    private String name;

    // 状态
    private volatile AgentState state = AgentState.IDLE;

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

    @Setter(AccessLevel.PROTECTED)
    private String finalAnswer;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Predicate<AgentEvent> eventSink = event -> true;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private String lastStepResult;

    private String execute(String userPrompt) {
        if (state != AgentState.IDLE) {
            throw new IllegalStateException("无法从当前状态运行 Agent: " + state);
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new IllegalArgumentException("不能使用空提示词");
        }
        state = AgentState.RUNNING;
        messageList.add(new UserMessage(userPrompt));

        for (int i = 0; i < maxSteps && state == AgentState.RUNNING; i++) {
            checkCancellation();
            currentStep = i + 1;
            log.info("Executing step {}/{}", currentStep, maxSteps);
            lastStepResult = step();
            checkCancellation();
        }

        // 所有工具结果先进入模型上下文，再单独生成面向用户的最终答复。
        publishEvent(AgentEvent.progress("summary", "正在整理最终答复", "running"));
        String answer = buildFinalAnswer();
        checkCancellation();
        if (StrUtil.isBlank(answer)) {
            throw new IllegalStateException("未能生成有效答复，请重试");
        }
        finalAnswer = answer;
        publishEvent(AgentEvent.answer(answer));
        publishEvent(AgentEvent.progress("summary", "答复已整理完成", "completed"));
        return answer;
    }

    public String run(String userPrompt) {
        try {
            String answer = execute(userPrompt);
            state = AgentState.FINISHED;
            return answer;
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("Error executing agent", e);
            return "执行错误" + e.getMessage();
        } finally {
            cleanup();
        }
    }

    public SseEmitter runStream(String userPrompt) {
        StreamSession session = new StreamSession(userPrompt);
        session.start();
        return session.emitter;
    }

    private void executeStream(String userPrompt, StreamSession session) {
        boolean successful = false;
        eventSink = session::sendEvent;
        try {
            if (session.closed.get()) {
                return;
            }
            execute(userPrompt);
            successful = session.send(SseEmitter.event().name("done").data("[DONE]"));
        } catch (Exception e) {
            if (!session.closed.get()) {
                state = AgentState.ERROR;
                log.error("Error executing agent", e);
                session.sendError("执行错误：" + e.getMessage());
            }
        } finally {
            eventSink = event -> true;
            session.complete(successful);
            // 仅由执行线程清理，避免回调与尚未退出的工具调用重复清理资源。
            cleanup();
        }
    }

    protected String buildFinalAnswer() {
        return StrUtil.isNotBlank(finalAnswer) ? finalAnswer : lastStepResult;
    }

    protected void publishEvent(AgentEvent event) {
        if (!eventSink.test(event)) {
            throw new CancellationException("SSE 连接已关闭");
        }
    }

    protected void checkCancellation() {
        if (state == AgentState.ERROR || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("任务已停止");
        }
    }

    private final class StreamSession {

        private final SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        private final AtomicBoolean closed = new AtomicBoolean();
        private final Thread worker;
        private final Thread heartbeat;

        private StreamSession(String userPrompt) {
            worker = Thread.ofVirtual().name("agent-stream").unstarted(() -> executeStream(userPrompt, this));
            heartbeat = Thread.ofVirtual().name("agent-stream-heartbeat").unstarted(this::sendHeartbeats);

            // 先注册生命周期回调，再启动任务，避免快速结束或断线时遗漏通知。
            emitter.onTimeout(() -> {
                log.warn("Agent SSE request timed out after {} ms", STREAM_TIMEOUT_MILLIS);
                stop();
                // Spring 在超时回调前已禁止 send；这里只结束请求，不再写入 SSE 或 JSON。
                emitter.complete();
            });
            emitter.onError(error -> stop());
            emitter.onCompletion(this::stop);
        }

        private void start() {
            heartbeat.start();
            worker.start();
        }

        private boolean send(SseEmitter.SseEventBuilder event) {
            if (closed.get()) {
                return false;
            }
            try {
                emitter.send(event);
                return true;
            } catch (IOException | IllegalStateException e) {
                // 关闭可能恰好发生在检查与发送之间，作为连接结束处理，不再次发送错误。
                stop();
                log.debug("Agent SSE connection is no longer writable", e);
                return false;
            }
        }

        private boolean sendEvent(AgentEvent event) {
            return send(SseEmitter.event().name(event.type()).data(event, MediaType.APPLICATION_JSON));
        }

        private void sendError(String message) {
            send(SseEmitter.event().name("error").data(message));
        }

        private void sendHeartbeats() {
            try {
                while (!closed.get()) {
                    Thread.sleep(HEARTBEAT_INTERVAL_MILLIS);
                    // SSE 注释不进入聊天内容，同时帮助代理保活并及时发现客户端断线。
                    if (!send(SseEmitter.event().comment("keepalive"))) {
                        return;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private void stop() {
            if (closed.compareAndSet(false, true)) {
                state = AgentState.ERROR;
                worker.interrupt();
                heartbeat.interrupt();
            }
        }

        private void complete(boolean successful) {
            if (closed.compareAndSet(false, true)) {
                if (successful) {
                    state = AgentState.FINISHED;
                }
                heartbeat.interrupt();
                emitter.complete();
            }
        }
    }

    public abstract String step();

    protected void cleanup() {
        // 清理资源
    }
}
