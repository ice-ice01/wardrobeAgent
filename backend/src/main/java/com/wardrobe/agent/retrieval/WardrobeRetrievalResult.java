package com.wardrobe.agent.retrieval;

import com.wardrobe.agent.wardrobe.WardrobeItem;

import java.util.List;
import java.util.Map;

/**
 * 语义检索输出。除候选商品外还携带召回数量、耗时和回退状态，供事件与日志观测。
 */
public record WardrobeRetrievalResult(
        List<WardrobeItem> candidates,
        int semanticRecallCount,
        Map<String, Integer> slotCounts,
        long elapsedMillis,
        boolean semantic,
        boolean fallback,
        String model,
        String indexVersion
) {}
