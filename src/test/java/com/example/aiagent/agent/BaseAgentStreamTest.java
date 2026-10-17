package com.example.aiagent.agent;

import com.example.aiagent.agent.model.AgentState;
import com.example.aiagent.exception.GlobalExceptionHandler;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CancellationException;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BaseAgentStreamTest {

    @Test
    void completesNormallyWithExplicitTimeoutAndOneCleanup() throws Exception {
        TestAgent agent = new TestAgent(current -> {
            current.setState(AgentState.FINISHED);
            return "工具结果";
        });
        MockMvc mockMvc = createMockMvc(agent);

        MvcResult result = startStream(mockMvc);
        assertEquals(600_000L, result.getRequest().getAsyncContext().getTimeout());
        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("event:answer")))
                .andExpect(content().string(containsString("\"content\":\"工具结果\"")))
                .andExpect(content().string(containsString("event:done\ndata:[DONE]")));

        awaitCleanup(agent);
        assertEquals(AgentState.FINISHED, agent.getState());
        assertEquals(1, agent.steps.get());
    }

    @Test
    void reportsStepFailuresAsSseErrors() throws Exception {
        TestAgent agent = new TestAgent(current -> {
            throw new IllegalArgumentException("工具失败");
        });
        MockMvc mockMvc = createMockMvc(agent);

        MvcResult result = startStream(mockMvc);
        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:error\ndata:执行错误：工具失败")));

        awaitCleanup(agent);
        assertEquals(AgentState.ERROR, agent.getState());
    }

    @Test
    void completesAtMaxSteps() throws Exception {
        TestAgent agent = new TestAgent(current -> "工具结果");
        agent.setMaxSteps(2);
        MockMvc mockMvc = createMockMvc(agent);

        MvcResult result = startStream(mockMvc);
        mockMvc.perform(asyncDispatch(result))
                .andExpect(content().string(containsString("event:answer")))
                .andExpect(content().string(containsString("event:done")));

        awaitCleanup(agent);
        assertEquals(2, agent.steps.get());
        assertEquals(AgentState.FINISHED, agent.getState());
    }

    @Test
    void rejectsBlankPromptsWithoutStartingSteps() throws Exception {
        TestAgent agent = new TestAgent(current -> "不应执行");
        MockMvc mockMvc = createMockMvc(agent);
        MvcResult result = mockMvc.perform(get("/stream").param("prompt", " "))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(content().string(containsString("event:error\ndata:执行错误：不能使用空提示词")));

        awaitCleanup(agent);
        assertEquals(0, agent.steps.get());
    }

    @Test
    void stopsOnTimeoutAndDiscardsLateToolResults() throws Exception {
        assertStopsAfterConnectionEvent("timeout");
    }

    @Test
    void stopsOnClientDisconnectAndDiscardsLateToolResults() throws Exception {
        assertStopsAfterConnectionEvent("error");
    }

    @Test
    void stopsOnCompletionAndDiscardsLateToolResults() throws Exception {
        assertStopsAfterConnectionEvent("completion");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void handlesOtherAsyncTimeoutsWithoutWritingJsonIntoSse(boolean committed) throws Exception {
        MockMvc mockMvc = createMockMvc(new TestAgent(current -> "unused"));
        MvcResult result = mockMvc.perform(get("/timeout")
                        .param("committed", String.valueOf(committed))
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        fireConnectionEvent(result, "timeout");
        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().is(committed ? 200 : 503))
                .andExpect(content().string(committed ? "data:started\n\n" : ""))
                .andExpect(dispatch -> assertInstanceOf(AsyncRequestTimeoutException.class,
                        dispatch.getResolvedException()));
    }

    @Test
    void doesNotStartToolsWhenThinkingWasCancelled() {
        AtomicInteger toolCalls = new AtomicInteger();
        ReActAgent agent = new ReActAgent() {
            @Override
            public boolean thinking() {
                setState(AgentState.ERROR);
                return true;
            }

            @Override
            public String act() {
                toolCalls.incrementAndGet();
                return "不应执行";
            }
        };
        agent.setState(AgentState.RUNNING);

        assertThrows(CancellationException.class, agent::step);

        assertEquals(0, toolCalls.get());
    }

    private void assertStopsAfterConnectionEvent(String event) throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TestAgent agent = new TestAgent(current -> {
            entered.countDown();
            // 模拟忽略中断、仍晚返回结果的第三方工具，确认关闭标记也能拦住后续发送。
            while (true) {
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("工具未释放");
                    }
                    return "晚返回的工具结果";
                } catch (InterruptedException e) {
                    interrupted.countDown();
                }
            }
        });
        MockMvc mockMvc = createMockMvc(agent);

        try {
            MvcResult result = startStream(mockMvc);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            fireConnectionEvent(result, event);
            assertTrue(interrupted.await(2, TimeUnit.SECONDS));
            release.countDown();
            awaitCleanup(agent);

            assertEquals(AgentState.ERROR, agent.getState());
            assertEquals(1, agent.steps.get());
            assertFalse(result.getResponse().getContentAsString().contains("晚返回的工具结果"));
            if (event.equals("timeout")) {
                mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());
            }
        } finally {
            release.countDown();
        }
    }

    private MockMvc createMockMvc(TestAgent agent) {
        return MockMvcBuilders.standaloneSetup(new TestController(agent))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new MappingJackson2HttpMessageConverter())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    private MvcResult startStream(MockMvc mockMvc) throws Exception {
        return mockMvc.perform(get("/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    private void fireConnectionEvent(MvcResult result, String type) throws IOException {
        MockAsyncContext context = (MockAsyncContext) result.getRequest().getAsyncContext();
        AsyncEvent event = new AsyncEvent(context, new IOException("client disconnected"));
        for (AsyncListener listener : context.getListeners()) {
            switch (type) {
                case "timeout" -> listener.onTimeout(event);
                case "error" -> listener.onError(event);
                case "completion" -> listener.onComplete(event);
                default -> throw new IllegalArgumentException(type);
            }
        }
    }

    private void awaitCleanup(TestAgent agent) throws InterruptedException {
        assertTrue(agent.cleaned.await(2, TimeUnit.SECONDS));
        assertEquals(1, agent.cleanupCalls.get());
    }

    @FunctionalInterface
    private interface StepAction {
        String run(TestAgent agent) throws Exception;
    }

    private static final class TestAgent extends BaseAgent {

        private final StepAction action;
        private final AtomicInteger steps = new AtomicInteger();
        private final AtomicInteger cleanupCalls = new AtomicInteger();
        private final CountDownLatch cleaned = new CountDownLatch(1);

        private TestAgent(StepAction action) {
            this.action = action;
        }

        @Override
        public String step() {
            steps.incrementAndGet();
            try {
                return action.run(this);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        protected void cleanup() {
            cleanupCalls.incrementAndGet();
            cleaned.countDown();
        }
    }

    @RestController
    private static final class TestController {

        private final TestAgent agent;

        private TestController(TestAgent agent) {
            this.agent = agent;
        }

        @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        SseEmitter stream(@RequestParam(defaultValue = "执行任务") String prompt) {
            return agent.runStream(prompt);
        }

        @GetMapping(value = "/timeout", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        SseEmitter timeout(@RequestParam boolean committed) throws IOException {
            SseEmitter emitter = new SseEmitter(1L);
            if (committed) {
                emitter.send("started");
            }
            return emitter;
        }
    }
}
