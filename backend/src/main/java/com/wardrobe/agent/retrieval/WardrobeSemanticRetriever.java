package com.wardrobe.agent.retrieval;

import com.wardrobe.agent.common.BusinessException;
import com.wardrobe.agent.wardrobe.WardrobeItem;
import com.wardrobe.agent.wardrobe.WardrobeSlot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
/**
 * 从大衣橱中召回“与场景相关且彼此有差异”的小候选集。
 *
 * <p>流程是：业务硬过滤 -> 补齐/更新向量 -> 余弦相似度排序 -> 分 slot 召回
 * -> MMR 多样性重排 -> 强制保留锁定商品和必需位置。它只缩小候选范围，
 * 最终穿搭仍由 AiGateway 选择并由 AgentService 校验。</p>
 */
public class WardrobeSemanticRetriever {
    private static final Logger log = LoggerFactory.getLogger(WardrobeSemanticRetriever.class);
    private static final List<WardrobeSlot> REQUIRED_SEPARATE_SLOTS =
            List.of(WardrobeSlot.INNER_TOP, WardrobeSlot.BOTTOM, WardrobeSlot.SHOES);

    private final WardrobeEmbeddingIndexer indexer;
    private final WardrobeEmbeddingStore store;
    private final WardrobeRetrievalProperties properties;

    public WardrobeSemanticRetriever(WardrobeEmbeddingIndexer indexer, WardrobeEmbeddingStore store,
                                     WardrobeRetrievalProperties properties) {
        this.indexer = indexer;
        this.store = store;
        this.properties = properties;
    }

    /** 执行一次语义召回；失败时根据配置回退到传统按 slot 截取逻辑。 */
    public WardrobeRetrievalResult retrieve(WardrobeRetrievalRequest request) {
        long started = System.nanoTime();
        List<WardrobeItem> eligible = request.eligibleItems().stream()
                .filter(item -> request.userId().equals(item.getUserId()))
                .filter(item -> !item.isDeleted())
                .filter(item -> !request.excludedItemIds().contains(item.getId()))
                .toList();
        // 小衣橱直接交给搭配模型，避免一次没有收益的 Embedding 调用。
        if (!properties.isEnabled() || eligible.size() <= properties.getWardrobeThreshold()) {
            WardrobeRetrievalResult result = result(eligible, 0, started, false, false);
            observe(eligible.size(), result);
            return result;
        }

        try {
            // 先补缺失索引，再以查询向量维度为准修复旧模型留下的维度不一致记录。
            indexer.ensureIndexed(eligible);
            float[] queryVector = indexer.embedQuery(request.scene());
            indexer.ensureIndexed(eligible, queryVector.length);
            Map<String, WardrobeItem> eligibleById = eligible.stream()
                    .collect(Collectors.toMap(WardrobeItem::getId, Function.identity()));
            Map<String, WardrobeEmbeddingRecord> records = store.findByUserAndVersion(
                            request.userId(), properties.getModel(), properties.getIndexVersion()).stream()
                    .filter(record -> request.userId().equals(record.userId()))
                    .filter(record -> eligibleById.containsKey(record.itemId()))
                    .filter(record -> record.dimension() == queryVector.length)
                    .collect(Collectors.toMap(WardrobeEmbeddingRecord::itemId, Function.identity(), (left, right) -> left));
            if (records.isEmpty()) throw new WardrobeEmbeddingException("No current wardrobe embeddings available");

            List<float[]> recentVectors = request.recentlyShownItemIds().stream()
                    .map(records::get).filter(java.util.Objects::nonNull).map(WardrobeEmbeddingRecord::vector).toList();
            double penaltyScale = request.exploreMore() ? properties.getExplorePenaltyMultiplier() : 0.5;
            // 相关性来自查询向量与衣物向量的余弦相似度，最近展示惩罚单独扣分。
            List<ScoredItem> allScored = records.values().stream()
                    .map(record -> new ScoredItem(eligibleById.get(record.itemId()), record.vector(),
                            cosine(queryVector, record.vector()), recentPenalty(record, recentVectors,
                            request.recentlyShownItemIds(), penaltyScale)))
                    .sorted(Comparator.comparingDouble(ScoredItem::relevance).reversed()
                            .thenComparing(scored -> scored.item().getId()))
                    .toList();

            // 全局 Top-K 保证相关性；每个 slot 的额外召回防止候选都集中在某一品类。
            LinkedHashMap<String, ScoredItem> recall = new LinkedHashMap<>();
            allScored.stream().limit(properties.getInitialRecall()).forEach(item -> recall.put(item.item().getId(), item));
            for (WardrobeSlot slot : WardrobeSlot.values()) {
                allScored.stream().filter(item -> item.item().getSlot() == slot)
                        .limit(properties.getCandidatesPerSlot() * 2L)
                        .forEach(item -> recall.putIfAbsent(item.item().getId(), item));
            }

            // MMR 在每个 slot 内平衡“符合场景”和“不要彼此太相似”。
            Map<WardrobeSlot, List<ScoredItem>> bySlot = new EnumMap<>(WardrobeSlot.class);
            for (WardrobeSlot slot : WardrobeSlot.values()) {
                // 锁定商品会单独优先保留，不再占用该 slot 的 MMR 补充名额。
                List<ScoredItem> slotPool = recall.values().stream()
                        .filter(item -> item.item().getSlot() == slot)
                        .filter(item -> !request.lockedItemIds().contains(item.item().getId()))
                        .toList();
                bySlot.put(slot, mmr(slotPool, properties.getCandidatesPerSlot()));
            }

            // LinkedHashSet 同时保持稳定顺序并去重；锁定商品永远先进入候选。
            LinkedHashSet<WardrobeItem> candidates = new LinkedHashSet<>();
            eligible.stream().filter(item -> request.lockedItemIds().contains(item.getId())).forEach(candidates::add);
            for (int rank = 0; rank < properties.getCandidatesPerSlot(); rank++) {
                for (WardrobeSlot slot : WardrobeSlot.values()) {
                    List<ScoredItem> selected = bySlot.get(slot);
                    if (rank < selected.size()) candidates.add(selected.get(rank).item());
                }
            }
            supplementRequiredSlots(candidates, eligible);
            WardrobeRetrievalResult result = result(List.copyOf(candidates), recall.size(), started, true, false);
            observe(eligible.size(), result);
            return result;
        } catch (Exception exception) {
            if (!properties.isFallbackEnabled()) {
                throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "SEMANTIC_RETRIEVAL_UNAVAILABLE",
                        "衣橱语义检索暂时不可用");
            }
            WardrobeRetrievalResult result = result(fallbackPool(eligible, request.lockedItemIds()), 0,
                    started, false, true);
            log.warn("Wardrobe semantic retrieval fell back: eligible={}, model={}, indexVersion={}, cause={}",
                    eligible.size(), properties.getModel(), properties.getIndexVersion(), exception.getClass().getSimpleName());
            observe(eligible.size(), result);
            return result;
        }
    }

    /**
     * Maximal Marginal Relevance：每轮选择综合得分最高的商品，再据此惩罚相似候选。
     */
    private List<ScoredItem> mmr(List<ScoredItem> pool, int limit) {
        List<ScoredItem> remaining = new ArrayList<>(pool);
        List<ScoredItem> selected = new ArrayList<>();
        while (!remaining.isEmpty() && selected.size() < limit) {
            ScoredItem next = remaining.stream().max(Comparator
                    .comparingDouble((ScoredItem candidate) -> mmrScore(candidate, selected))
                    .thenComparing(candidate -> candidate.item().getId())).orElseThrow();
            selected.add(next);
            remaining.remove(next);
        }
        return selected;
    }

    /** MMR = 相关性奖励 - 与已选商品的最大相似度惩罚 - 最近展示惩罚。 */
    private double mmrScore(ScoredItem candidate, List<ScoredItem> selected) {
        double maximumSimilarity = selected.stream()
                .mapToDouble(item -> cosine(candidate.vector(), item.vector())).max().orElse(0.0);
        return properties.getRelevanceWeight() * candidate.relevance()
                - properties.getDiversityWeight() * maximumSimilarity
                - candidate.recentPenalty();
    }

    /** “换一套”时提高惩罚，减少原商品或视觉上高度相似商品再次出现。 */
    private double recentPenalty(WardrobeEmbeddingRecord record, List<float[]> recentVectors,
                                 Set<String> recentlyShownIds, double scale) {
        if (recentlyShownIds.contains(record.itemId())) return properties.getRecentItemPenalty() * scale;
        double similarity = recentVectors.stream().mapToDouble(vector -> cosine(record.vector(), vector))
                .filter(value -> value > 0).max().orElse(0.0);
        return properties.getRecentItemPenalty() * scale * similarity * 0.5;
    }

    /** 语义召回漏掉基础穿搭位置时，从合法衣橱中兜底补入至少一件。 */
    private void supplementRequiredSlots(LinkedHashSet<WardrobeItem> candidates, List<WardrobeItem> eligible) {
        for (WardrobeSlot slot : REQUIRED_SEPARATE_SLOTS) {
            if (candidates.stream().noneMatch(item -> item.getSlot() == slot)) {
                eligible.stream().filter(item -> item.getSlot() == slot).findFirst().ifPresent(candidates::add);
            }
        }
    }

    /** Embedding 服务不可用时使用旧策略：保留锁定商品，并按位置截取固定数量。 */
    private List<WardrobeItem> fallbackPool(List<WardrobeItem> eligible, Set<String> locked) {
        if (eligible.size() <= properties.getWardrobeThreshold()) return eligible;
        LinkedHashSet<WardrobeItem> result = new LinkedHashSet<>();
        eligible.stream().filter(item -> locked.contains(item.getId())).forEach(result::add);
        for (WardrobeSlot slot : WardrobeSlot.values()) {
            eligible.stream().filter(item -> item.getSlot() == slot)
                    .limit(properties.getFallbackCandidatesPerSlot()).forEach(result::add);
        }
        return List.copyOf(result);
    }

    private WardrobeRetrievalResult result(List<WardrobeItem> candidates, int recall, long started,
                                           boolean semantic, boolean fallback) {
        Map<String, Integer> slotCounts = new LinkedHashMap<>();
        for (WardrobeSlot slot : WardrobeSlot.values()) {
            slotCounts.put(slot.name(), (int) candidates.stream().filter(item -> item.getSlot() == slot).count());
        }
        return new WardrobeRetrievalResult(candidates, recall, slotCounts,
                (System.nanoTime() - started) / 1_000_000, semantic, fallback,
                properties.getModel(), properties.getIndexVersion());
    }

    private void observe(int eligibleCount, WardrobeRetrievalResult result) {
        log.info("Wardrobe retrieval completed: eligible={}, semanticRecall={}, candidates={}, slotCounts={}, elapsedMs={}, fallback={}, model={}, indexVersion={}",
                eligibleCount, result.semanticRecallCount(), result.candidates().size(), result.slotCounts(),
                result.elapsedMillis(), result.fallback(), result.model(), result.indexVersion());
    }

    /**
     * 计算两个向量的余弦相似度。方向越接近结果越接近 1，语义通常也越相关。
     */
    static double cosine(float[] left, float[] right) {
        if (left.length != right.length) return 0.0;
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm == 0 || rightNorm == 0) return 0.0;
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    /** 检索阶段使用的临时对象，不会直接暴露给前端。 */
    private record ScoredItem(WardrobeItem item, float[] vector, double relevance, double recentPenalty) {}
}
