package com.wardrobe.agent.outfit;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** 返回前端的完整搭配 DTO，包含检索覆盖信息和经过校验的商品快照。 */
public record OutfitView(
        String id, String recommendationRunId, String title, String scene, int version, OutfitStatus status, boolean complete, Set<String> missingSlots,
        String reason, String explorationStatus, int wardrobeTotal, int eligibleTotal, int consideredCount, boolean hasMore,
        List<Item> selectedItems, Instant createdAt
) {
    /** 方案中的单件衣物展示数据。 */
    public record Item(String itemId, String slot, String name, String imageUrl, boolean locked) {}
}
