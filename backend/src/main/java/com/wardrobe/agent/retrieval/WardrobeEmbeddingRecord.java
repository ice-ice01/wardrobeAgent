package com.wardrobe.agent.retrieval;

import com.wardrobe.agent.wardrobe.WardrobeSlot;

/**
 * 一条持久化的衣物 Embedding。模型、版本和维度共同限定哪些向量可以直接比较。
 */
public record WardrobeEmbeddingRecord(
        String itemId,
        String userId,
        WardrobeSlot slot,
        String model,
        String indexVersion,
        int dimension,
        String contentHash,
        String metadataJson,
        float[] vector
) {}
