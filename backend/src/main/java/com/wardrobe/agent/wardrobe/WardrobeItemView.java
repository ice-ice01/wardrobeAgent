package com.wardrobe.agent.wardrobe;

import java.time.Instant;
import java.util.Set;

/** 面向 API 的衣物 DTO，避免直接序列化 JPA 实体和内部文件字段。 */
public record WardrobeItemView(
        String id, String name, String category, WardrobeSlot slot, String color, String material,
        Set<String> seasonTags, Set<String> sceneTags, Set<String> styleTags, String imageUrl,
        String imageSource, String imageStatus, Integer warmthLevel, Integer breathabilityLevel, Instant updatedAt
) {
    static WardrobeItemView from(WardrobeItem item) {
        return new WardrobeItemView(item.getId(), item.getName(), item.getCategory(), item.getSlot(), item.getColor(),
                item.getMaterial(), item.getSeasonTags(), item.getSceneTags(), item.getStyleTags(), item.getImageUrl(),
                item.getImageSource(), item.getImageStatus(), item.getWarmthLevel(), item.getBreathabilityLevel(), item.getUpdatedAt());
    }
}
