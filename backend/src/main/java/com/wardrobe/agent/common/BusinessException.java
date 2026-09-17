package com.wardrobe.agent.common;

import org.springframework.http.HttpStatus;

/** 携带 HTTP 状态和稳定业务错误码，由 GlobalExceptionHandler 统一转换为 JSON。 */
public class BusinessException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public BusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
}
