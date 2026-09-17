package com.wardrobe.agent.retrieval;

import com.wardrobe.agent.wardrobe.WardrobeItem;

import java.util.List;
import java.util.Set;

/** 语义检索的完整输入；eligibleItems 已经过第一层业务过滤。 */
public record WardrobeRetrievalRequest(
        String userId,
        String scene,
        List<WardrobeItem> eligibleItems,
        Set<String> lockedItemIds,
        Set<String> excludedItemIds,
        Set<String> recentlyShownItemIds,
        boolean exploreMore
) {}
