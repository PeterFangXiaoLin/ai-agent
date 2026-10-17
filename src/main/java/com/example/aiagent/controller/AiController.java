package com.example.aiagent.controller;

import com.example.aiagent.agent.Manus;
import com.example.aiagent.app.LoveApp;
import com.example.aiagent.common.BaseResponse;
import com.example.aiagent.common.ResultUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private LoveApp loveApp;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashScopeChatModel;

    @GetMapping("/love_app/chat/sync")
    public BaseResponse<String> doChatWithLoveAppSync(String message, String chatId) {
        return ResultUtils.success(loveApp.doChat(message, chatId));
    }

    @GetMapping(value = "/love_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithLoveAppSSE(String message, String chatId, HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        // 直接返回流，让 Spring MVC 按 SSE 写出，不能套用普通 JSON 响应包装。
        return loveApp.doChatByStream(message, chatId);
    }

    @GetMapping(value = "/love_app/chat/server_sent_event", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> doChatWithLoveAppServerSentEvent(String message, String chatId, HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        return loveApp.doChatByStream(message, chatId)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    @GetMapping(value = "/love_app/chat/sse_emitter", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter doChatWithLoveAppSseEmitter(String message, String chatId, HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        SseEmitter sseEmitter = new SseEmitter(300000L);
        loveApp.doChatByStream(message, chatId)
                .subscribe(chunk -> {
                            try {
                                sseEmitter.send(chunk);
                            } catch (IOException e) {
                                sseEmitter.completeWithError(e);
                            }
                        },
                        // 异常处理
                        sseEmitter::completeWithError,
                        // 完成处理
                        sseEmitter::complete);
        return sseEmitter;
    }

    @GetMapping(value = "/manus/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter doChatWithManus(String message) {
        Manus manus = new Manus(allTools, dashScopeChatModel);
        return manus.runStream(message);
    }
}
