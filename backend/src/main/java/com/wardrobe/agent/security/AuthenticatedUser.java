package com.wardrobe.agent.security;

/** JWT 验证后放入 Spring SecurityContext 的精简身份对象。 */
public record AuthenticatedUser(String id, String username, String displayName) {}
