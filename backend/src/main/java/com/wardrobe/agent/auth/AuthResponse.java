package com.wardrobe.agent.auth;

/** 登录响应 DTO；不会把密码哈希等持久化字段暴露给客户端。 */
public record AuthResponse(String token, UserView user) {
    public record UserView(String id, String username, String displayName, long wardrobeRevision) {}
}
