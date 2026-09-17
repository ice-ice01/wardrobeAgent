package com.wardrobe.agent.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** 使用 @RestControllerAdvice 把不同异常统一转换为 ApiError，避免各 Controller 重复处理。 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiError> handleBusiness(BusinessException exception) {
        return response(exception.getStatus(), exception.getCode(), exception.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求参数校验失败", fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraint(ConstraintViolationException exception) {
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handleUploadSize() {
        return response(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE", "图片不能超过 10 MB", Map.of());
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    void handleDisconnectedClient(AsyncRequestNotUsableException exception) {
        log.debug("Streaming client disconnected: {}", exception.getMessage());
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    void handleStreamingTimeout(AsyncRequestTimeoutException exception) {
        log.debug("Streaming request timed out: {}", exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    /** 兜底异常只向客户端返回安全提示，详细堆栈保留在服务端日志。 */
    ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        log.error("Unhandled request failure", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务暂时不可用，请稍后重试", Map.of());
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String code, String message, Map<String, String> fields) {
        return ResponseEntity.status(status).body(new ApiError(code, message, MDC.get("requestId"), Instant.now(), fields));
    }
}
