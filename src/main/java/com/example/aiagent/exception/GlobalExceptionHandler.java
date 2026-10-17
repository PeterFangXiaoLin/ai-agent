package com.example.aiagent.exception;

import com.example.aiagent.common.BaseResponse;
import com.example.aiagent.common.ResultUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void asyncRequestTimeoutExceptionHandler(AsyncRequestTimeoutException e, HttpServletResponse response) {
        log.warn("Async request timed out");
        // 流式响应可能已经提交，不能再尝试用 BaseResponse 覆盖 SSE 响应体。
        if (!response.isCommitted()) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        }
    }

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }
}



