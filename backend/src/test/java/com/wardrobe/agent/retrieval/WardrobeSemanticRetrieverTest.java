package com.wardrobe.agent.retrieval;

import com.wardrobe.agent.wardrobe.WardrobeItem;
import com.wardrobe.agent.wardrobe.WardrobeItemCommand;
import com.wardrobe.agent.wardrobe.WardrobeSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 语义检索单元测试。
 *
 * <p>测试用关键词向量代替真实 OpenAI Embedding：结果完全可重复，而且可以精确验证
 * 小衣橱直通、大衣橱语义召回、MMR 多样性、历史降权、索引更新和故障降级。</p>
 */
class WardrobeSemanticRetrieverTest {
    private WardrobeRetrievalProperties properties;
    private InMemoryEmbeddingStore store;
    private WardrobeEmbeddingIndexer indexer;
    private WardrobeSemanticRetriever retriever;

    @BeforeEach
    void setUp() {
        properties = new WardrobeRetrievalProperties();
        properties.setWardrobeThreshold(3);
        properties.setInitialRecall(6);
        properties.setCandidatesPerSlot(2);
        properties.setFallbackCandidatesPerSlot(12);
        store = new InMemoryEmbeddingStore();
        indexer = new WardrobeEmbeddingIndexer(new KeywordEmbeddingModel(), store, properties, new ObjectMapper());
        retriever = new WardrobeSemanticRetriever(indexer, store, properties);
    }

    @Test
    /** 衣橱未超过阈值时保留所有合格商品，无需花费一次 Embedding 调用。 */
    void smallWardrobeKeepsEveryEligibleOwnedItemWithoutCallingEmbedding() {
        WardrobeItem top = item("user-a", "海边上衣", WardrobeSlot.INNER_TOP, Set.of("海边"));
        WardrobeItem bottom = item("user-a", "休闲短裤", WardrobeSlot.BOTTOM, Set.of("海边"));
        WardrobeItem excluded = item("user-a", "排除鞋", WardrobeSlot.SHOES, Set.of("海边"));
        WardrobeItem anotherUser = item("user-b", "其他用户鞋", WardrobeSlot.SHOES, Set.of("海边"));

        WardrobeRetrievalResult result = retriever.retrieve(request("user-a", List.of(top, bottom, excluded, anotherUser),
                Set.of(), Set.of(excluded.getId()), Set.of(), false));

        assertThat(result.candidates()).containsExactly(top, bottom);
        assertThat(result.semantic()).isFalse();
        assertThat(store.records).isEmpty();
    }

    @Test
    /** 大衣橱按语义召回，同时仍要满足用户归属、锁定、排除和品类覆盖约束。 */
    void largeWardrobeRecallsRelevantItemBeyondOldFirstTwelveAndPreservesConstraintsAndSlots() {
        List<WardrobeItem> items = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            items.add(item("user-a", "普通上衣" + index, WardrobeSlot.INNER_TOP, Set.of("日常")));
        }
        WardrobeItem relevantThirteenth = item("user-a", "三亚海边亚麻衬衫", WardrobeSlot.INNER_TOP, Set.of("海边"));
        WardrobeItem bottom = item("user-a", "海边短裤", WardrobeSlot.BOTTOM, Set.of("海边"));
        WardrobeItem shoes = item("user-a", "沙滩凉鞋", WardrobeSlot.SHOES, Set.of("海边"));
        WardrobeItem locked = items.getFirst();
        WardrobeItem excluded = items.get(1);
        WardrobeItem anotherUser = item("user-b", "其他用户的海边神衣", WardrobeSlot.INNER_TOP, Set.of("海边"));
        indexer.ensureIndexed(List.of(anotherUser));
        items.add(relevantThirteenth);
        items.add(bottom);
        items.add(shoes);
        items.add(anotherUser);

        WardrobeRetrievalResult result = retriever.retrieve(request("user-a", items, Set.of(locked.getId()),
                Set.of(excluded.getId()), Set.of(), false));

        assertThat(result.semantic()).isTrue();
        assertThat(result.fallback()).isFalse();
        assertThat(result.candidates()).contains(relevantThirteenth, locked, bottom, shoes)
                .doesNotContain(excluded, anotherUser);
        assertThat(result.slotCounts()).containsEntry("INNER_TOP", 3).containsEntry("BOTTOM", 1).containsEntry("SHOES", 1);
    }

    @Test
    /** “换一套”会降低刚展示商品的分数，让相似但未展示的商品更靠前。 */
    void exploreMoreDownranksRecentlyShownItemAndSimilarAlternativesLessAggressively() {
        WardrobeItem recentTop = item("user-a", "海边亚麻上衣A", WardrobeSlot.INNER_TOP, Set.of("海边"));
        WardrobeItem freshTop = item("user-a", "海边亚麻上衣B", WardrobeSlot.INNER_TOP, Set.of("海边"));
        WardrobeItem bottom = item("user-a", "海边短裤", WardrobeSlot.BOTTOM, Set.of("海边"));
        WardrobeItem shoes = item("user-a", "沙滩凉鞋", WardrobeSlot.SHOES, Set.of("海边"));

        WardrobeRetrievalResult result = retriever.retrieve(request("user-a", List.of(recentTop, freshTop, bottom, shoes),
                Set.of(), Set.of(), Set.of(recentTop.getId()), true));

        assertThat(result.candidates().indexOf(freshTop)).isLessThan(result.candidates().indexOf(recentTop));
    }

    @Test
    /** 商品内容、索引版本或向量维度变化时要重建索引；删除商品也要删除向量。 */
    void changedItemIsReindexedVersionChangesDoNotMixAndDeleteRemovesVector() {
        WardrobeItem top = item("user-a", "通勤衬衫", WardrobeSlot.INNER_TOP, Set.of("通勤"));
        indexer.ensureIndexed(List.of(top));
        WardrobeEmbeddingRecord original = store.findByUserAndVersion("user-a", properties.getModel(),
                properties.getIndexVersion()).getFirst();

        top.update(command("海边亚麻衬衫", WardrobeSlot.INNER_TOP, Set.of("海边")));
        indexer.ensureIndexed(List.of(top));
        WardrobeEmbeddingRecord updated = store.findByUserAndVersion("user-a", properties.getModel(),
                properties.getIndexVersion()).getFirst();
        assertThat(updated.contentHash()).isNotEqualTo(original.contentHash());

        String oldVersion = properties.getIndexVersion();
        properties.setIndexVersion("wardrobe-text-v2");
        indexer.ensureIndexed(List.of(top));
        assertThat(store.findByUserAndVersion("user-a", properties.getModel(), oldVersion)).isEmpty();
        assertThat(store.findByUserAndVersion("user-a", properties.getModel(), "wardrobe-text-v2")).hasSize(1);

        WardrobeEmbeddingIndexer changedDimensionIndexer = new WardrobeEmbeddingIndexer(
                new FiveDimensionEmbeddingModel(), store, properties, new ObjectMapper());
        changedDimensionIndexer.ensureIndexed(List.of(top), 5);
        assertThat(store.findByUserAndVersion("user-a", properties.getModel(), "wardrobe-text-v2").getFirst().dimension())
                .isEqualTo(5);

        changedDimensionIndexer.delete("user-a", top.getId());
        assertThat(store.findByUserAndVersion("user-a", properties.getModel(), "wardrobe-text-v2")).isEmpty();
    }

    @Test
    /** Embedding 服务不可用时走传统分品类候选池，保证推荐功能仍然可用。 */
    void embeddingFailureFallsBackToExistingPerSlotPool() {
        List<WardrobeItem> items = new ArrayList<>();
        for (int index = 0; index < 15; index++) {
            items.add(item("user-a", "上衣" + index, WardrobeSlot.INNER_TOP, Set.of("日常")));
        }
        WardrobeItem lockedOutsideFirstTwelve = items.get(14);
        WardrobeItem bottom = item("user-a", "长裤", WardrobeSlot.BOTTOM, Set.of("日常"));
        WardrobeItem shoes = item("user-a", "鞋", WardrobeSlot.SHOES, Set.of("日常"));
        items.add(bottom);
        items.add(shoes);
        WardrobeEmbeddingIndexer failingIndexer = new WardrobeEmbeddingIndexer(
                new FailingEmbeddingModel(), store, properties, new ObjectMapper());
        WardrobeSemanticRetriever fallbackRetriever = new WardrobeSemanticRetriever(failingIndexer, store, properties);

        WardrobeRetrievalResult result = fallbackRetriever.retrieve(request("user-a", items,
                Set.of(lockedOutsideFirstTwelve.getId()), Set.of(), Set.of(), false));

        assertThat(result.fallback()).isTrue();
        assertThat(result.candidates()).contains(lockedOutsideFirstTwelve, bottom, shoes);
        assertThat(result.candidates().stream().filter(item -> item.getSlot() == WardrobeSlot.INNER_TOP)).hasSize(13);
    }

    private static WardrobeRetrievalRequest request(String userId, List<WardrobeItem> eligible, Set<String> locked,
                                                    Set<String> excluded, Set<String> recent, boolean exploreMore) {
        return new WardrobeRetrievalRequest(userId, "我要去三亚海边", eligible, locked, excluded, recent, exploreMore);
    }

    private static WardrobeItem item(String userId, String name, WardrobeSlot slot, Set<String> scenes) {
        return new WardrobeItem(userId, command(name, slot, scenes), "/api/files/test", "UPLOAD", "file-test");
    }

    private static WardrobeItemCommand command(String name, WardrobeSlot slot, Set<String> scenes) {
        return new WardrobeItemCommand(name, slot.name(), slot, "蓝色", "亚麻", Set.of("夏季"), scenes,
                Set.of("休闲"), 1, 5, "file-test");
    }

    /** 测试专用内存存储，避免单元测试依赖数据库。 */
    private static class InMemoryEmbeddingStore implements WardrobeEmbeddingStore {
        private final Map<String, WardrobeEmbeddingRecord> records = new LinkedHashMap<>();

        @Override
        public List<WardrobeEmbeddingRecord> findByUserAndVersion(String userId, String model, String indexVersion) {
            return records.values().stream().filter(record -> userId.equals(record.userId()))
                    .filter(record -> model.equals(record.model()) && indexVersion.equals(record.indexVersion())).toList();
        }

        @Override
        public void saveAll(List<WardrobeEmbeddingRecord> newRecords) {
            newRecords.forEach(record -> {
                records.remove(record.itemId());
                records.put(record.itemId(), record);
            });
        }

        @Override
        public void delete(String userId, String itemId) {
            WardrobeEmbeddingRecord record = records.get(itemId);
            if (record != null && userId.equals(record.userId())) records.remove(itemId);
        }
    }

    /**
     * 测试专用 EmbeddingModel：把几个关键词映射到固定维度，模拟“语义接近则向量接近”。
     */
    private static class KeywordEmbeddingModel implements EmbeddingModel {
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = new ArrayList<>();
            for (int index = 0; index < request.getInstructions().size(); index++) {
                embeddings.add(new Embedding(vector(request.getInstructions().get(index)), index));
            }
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return vector(getEmbeddingContent(document));
        }

        private static float[] vector(String text) {
            return new float[]{contains(text, "海边", "三亚") ? 1f : 0f,
                    contains(text, "通勤", "办公") ? 1f : 0f,
                    contains(text, "BOTTOM", "短裤", "长裤") ? 0.2f : 0f,
                    contains(text, "SHOES", "鞋") ? 0.2f : 0f};
        }

        private static boolean contains(String text, String... values) {
            for (String value : values) if (text.contains(value)) return true;
            return false;
        }
    }

    /** 主动抛错，用于验证外部 Embedding API 故障时的降级逻辑。 */
    private static final class FailingEmbeddingModel extends KeywordEmbeddingModel {
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            throw new IllegalStateException("embedding unavailable");
        }
    }

    /** 返回不同维度的向量，用于验证模型升级后的重新建索引逻辑。 */
    private static final class FiveDimensionEmbeddingModel extends KeywordEmbeddingModel {
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = new ArrayList<>();
            for (int index = 0; index < request.getInstructions().size(); index++) {
                embeddings.add(new Embedding(new float[]{1, 0, 0, 0, 0}, index));
            }
            return new EmbeddingResponse(embeddings);
        }
    }
}
