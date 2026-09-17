package com.wardrobe.agent.common;

import java.time.Instant;
import java.util.Map;

/** 全部 REST 错误共享的稳定响应结构，requestId 可用于关联服务端日志。 */
public record ApiError(
        String code,
        String message,
        String requestId,
        Instant timestamp,
        Map<String, String> fieldErrors
) {}
