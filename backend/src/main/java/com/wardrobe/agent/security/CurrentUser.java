package com.wardrobe.agent.security;

import com.wardrobe.agent.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 从当前请求线程的 SecurityContext 中读取已认证用户。 */
public final class CurrentUser {
    private CurrentUser() {}

    /** 返回登录用户；没有有效身份时抛出统一的 401 业务异常。 */
    public static AuthenticatedUser require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        }
        return user;
    }
}
