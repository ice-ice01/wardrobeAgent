package com.wardrobe.agent.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 登录请求 DTO；校验注解由 Controller 上的 @Valid 触发。 */
public record LoginRequest(
        @NotBlank @Size(max = 80) String username,
        @NotBlank @Size(max = 120) String password
) {}
