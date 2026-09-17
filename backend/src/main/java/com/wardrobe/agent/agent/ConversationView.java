package com.wardrobe.agent.agent;

import com.wardrobe.agent.outfit.OutfitView;
import java.time.Instant;
import java.util.List;

/** 会话接口 DTO，同时聚合业务消息和该会话生成过的搭配。 */
public record ConversationView(String id, String title, Instant lastMessageAt, List<MessageView> messages, List<OutfitView> outfits) {
    /** 单条消息视图；recommendationRunId 用于关联生成它的推荐运行。 */
    public record MessageView(String id, String clientMessageId, String recommendationRunId, String role, String content, String status, Instant createdAt) {}
}
