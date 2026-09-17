package com.wardrobe.agent.tryon;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** 返回前端的试穿任务快照，也是 SSE task.status 事件的数据体。 */
public record TryOnView(
        String id, String planId, int planVersion, String userModelId, String provider, String effectiveProvider,
        boolean fallbackUsed, String fallbackReason, String resultKind, TryOnStatus status,
        String coverage, Set<String> selectedItemIds, Set<String> renderedItemIds, Set<String> unrenderedItemIds, String resultImageUrl,
        String errorCode, String errorMessage, long statusVersion, String retryFromTaskId, List<Item> items, Instant createdAt
) {
    public record Item(String itemId, String slot, String name, String imageUrl, boolean selected, boolean rendered,
                       Integer sequence, TryOnStageStatus status, String effectiveProvider, boolean fallbackUsed,
                       String fallbackReason, String resultKind, String resultImageUrl,
                       String errorCode, String errorMessage) {}
}
