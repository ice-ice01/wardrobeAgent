package com.wardrobe.agent.retrieval;

import java.util.List;

/** 向量持久化端口，使检索算法不依赖 MySQL 或某个具体向量数据库。 */
public interface WardrobeEmbeddingStore {
    /** 按租户和索引版本读取可比较的衣物向量。 */
    List<WardrobeEmbeddingRecord> findByUserAndVersion(String userId, String model, String indexVersion);

    /** 批量新增或覆盖衣物向量。 */
    void saveAll(List<WardrobeEmbeddingRecord> records);

    /** 删除某个用户衣物对应的所有活动向量。 */
    void delete(String userId, String itemId);
}
