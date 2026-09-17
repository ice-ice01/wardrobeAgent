package com.wardrobe.agent.retrieval;

import com.wardrobe.agent.wardrobe.WardrobeItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
/**
 * 负责把衣物的结构化属性转换为文本 Embedding，并维护对应索引记录。
 *
 * <p>Embedding 模型把语义文本变成 float 向量。内容哈希、模型名和索引版本
 * 用于判断旧向量是否还能复用，避免每次搭配都重复调用 Embedding API。</p>
 */
public class WardrobeEmbeddingIndexer {
    private static final Logger log = LoggerFactory.getLogger(WardrobeEmbeddingIndexer.class);
    private final EmbeddingModel embeddingModel;
    private final WardrobeEmbeddingStore store;
    private final WardrobeRetrievalProperties properties;
    private final ObjectMapper json;

    public WardrobeEmbeddingIndexer(EmbeddingModel embeddingModel, WardrobeEmbeddingStore store,
                                    WardrobeRetrievalProperties properties, ObjectMapper json) {
        this.embeddingModel = embeddingModel;
        this.store = store;
        this.properties = properties;
        this.json = json;
    }

    /** 确保给定衣物拥有当前模型和索引版本的向量。 */
    public IndexingReport ensureIndexed(List<WardrobeItem> items) {
        return ensureIndexed(items, null);
    }

    /**
     * 增量更新缺失或过期的向量；expectedDimension 用来淘汰维度不匹配的历史向量。
     */
    public IndexingReport ensureIndexed(List<WardrobeItem> items, Integer expectedDimension) {
        if (!properties.isEnabled() || items.isEmpty()) return new IndexingReport(0, 0);
        String userId = singleUser(items);
        Map<String, WardrobeEmbeddingRecord> current = new LinkedHashMap<>();
        store.findByUserAndVersion(userId, properties.getModel(), properties.getIndexVersion())
                .forEach(record -> current.put(record.itemId(), record));

        // 只有文本、slot、向量维度或索引版本发生变化的商品才需要重新调用模型。
        List<IndexDocument> stale = items.stream()
                .filter(item -> userId.equals(item.getUserId()) && !item.isDeleted())
                .map(item -> new IndexDocument(item, embeddingText(item)))
                .filter(document -> {
                    WardrobeEmbeddingRecord record = current.get(document.item().getId());
                    return record == null || !record.contentHash().equals(hash(document.text()))
                            || record.slot() != document.item().getSlot()
                            || expectedDimension != null && record.dimension() != expectedDimension;
                })
                .toList();
        if (stale.isEmpty()) return new IndexingReport(0, current.size());

        // 批量 Embedding 通常比逐件请求更省网络往返和模型调用开销。
        List<float[]> vectors = embeddingModel.embed(stale.stream().map(IndexDocument::text).toList());
        if (vectors.size() != stale.size()) throw new WardrobeEmbeddingException("Embedding result count mismatch");
        List<WardrobeEmbeddingRecord> records = new ArrayList<>(stale.size());
        int dimension = -1;
        for (int index = 0; index < stale.size(); index++) {
            IndexDocument document = stale.get(index);
            float[] vector = vectors.get(index);
            if (vector == null || vector.length == 0) throw new WardrobeEmbeddingException("Embedding result is empty");
            if (dimension < 0) dimension = vector.length;
            if (vector.length != dimension) throw new WardrobeEmbeddingException("Embedding dimensions are inconsistent");
            records.add(new WardrobeEmbeddingRecord(
                    document.item().getId(), userId, document.item().getSlot(), properties.getModel(),
                    properties.getIndexVersion(), vector.length, hash(document.text()), metadata(document.item()), vector));
        }
        store.saveAll(records);
        long newItems = records.stream().filter(record -> !current.containsKey(record.itemId())).count();
        return new IndexingReport(records.size(), current.size() + (int) newItems);
    }

    /**
     * 衣物写入后尽力刷新索引。索引失败只记录日志，不能让核心衣橱 CRUD 一并失败。
     */
    public void indexBestEffort(WardrobeItem item) {
        if (!properties.isEnabled()) return;
        try {
            ensureIndexed(List.of(item));
        } catch (Exception exception) {
            log.warn("Wardrobe embedding update deferred: itemId={}, model={}, indexVersion={}, cause={}",
                    item.getId(), properties.getModel(), properties.getIndexVersion(), exception.getClass().getSimpleName());
        }
    }

    /** 每次检索时将用户场景转换为查询向量，该向量通常不需要持久化。 */
    public float[] embedQuery(String scene) {
        float[] vector = embeddingModel.embed(scene);
        if (vector == null || vector.length == 0) throw new WardrobeEmbeddingException("Query embedding is empty");
        return vector;
    }

    /** 衣物删除时同步删除其向量，避免失效商品继续参与召回。 */
    public void delete(String userId, String itemId) {
        store.delete(userId, itemId);
    }

    /**
     * 构造稳定的语义文档。字段顺序和排序必须稳定，否则相同业务内容会产生不同哈希。
     */
    static String embeddingText(WardrobeItem item) {
        return """
                名称：%s
                类别：%s
                位置：%s
                颜色：%s
                材质：%s
                季节：%s
                场景：%s
                风格：%s
                保暖等级：%s
                透气等级：%s
                """.formatted(item.getName(), item.getCategory(), item.getSlot().name(), item.getColor(), item.getMaterial(),
                sorted(item.getSeasonTags()), sorted(item.getSceneTags()), sorted(item.getStyleTags()),
                value(item.getWarmthLevel()), value(item.getBreathabilityLevel())).trim();
    }

    private String metadata(WardrobeItem item) {
        try {
            return json.writeValueAsString(Map.of(
                    "itemId", item.getId(),
                    "slot", item.getSlot().name(),
                    "imageSource", item.getImageSource(),
                    "indexVersion", properties.getIndexVersion()));
        } catch (Exception exception) {
            throw new WardrobeEmbeddingException("Embedding metadata serialization failed", exception);
        }
    }

    /** 确保一次批量索引不会混入其他用户的数据。 */
    private static String singleUser(List<WardrobeItem> items) {
        String userId = items.getFirst().getUserId();
        if (items.stream().anyMatch(item -> !userId.equals(item.getUserId()))) {
            throw new WardrobeEmbeddingException("Cannot index wardrobe items from multiple users in one batch");
        }
        return userId;
    }

    private static String sorted(java.util.Set<String> values) {
        return values.stream().sorted(Comparator.naturalOrder()).reduce((left, right) -> left + "、" + right).orElse("UNKNOWN");
    }

    private static String value(Integer value) { return value == null ? "UNKNOWN" : value.toString(); }

    /** 内容哈希用于快速判断衣物描述是否变化，本身不是 Embedding。 */
    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** 等待批量 Embedding 的衣物及其语义文本。 */
    private record IndexDocument(WardrobeItem item, String text) {}

    /** 本次实际重建数量以及当前可用索引总数。 */
    public record IndexingReport(int indexedCount, int availableCount) {}
}
