package com.wardrobe.agent.agent;

import com.wardrobe.agent.wardrobe.WardrobeItem;
import com.wardrobe.agent.wardrobe.WardrobeSlot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "mock")
/**
 * 使用确定性打分规则代替真实搭配模型，确保测试结果可重复且不产生 API 费用。
 */
public class MockAiGateway implements AiGateway {
    @Override
    /** 按标签匹配度排序，并以 alternativeIndex 轮换同一位置的候选。 */
    public AiProposal propose(String scene, List<WardrobeItem> candidates, Set<String> lockedItemIds,
                              int alternativeIndex, Set<String> previousCombinationKeys) {
        List<WardrobeItem> ranked = candidates.stream()
                .sorted(Comparator.comparingInt((WardrobeItem item) -> score(item, scene)).reversed().thenComparing(WardrobeItem::getId))
                .toList();
        Map<WardrobeSlot, WardrobeItem> selected = new LinkedHashMap<>();
        ranked.stream().filter(item -> lockedItemIds.contains(item.getId())).forEach(item -> selected.put(item.getSlot(), item));
        boolean dress = selected.containsKey(WardrobeSlot.DRESS);
        List<WardrobeSlot> required = dress
                ? List.of(WardrobeSlot.DRESS, WardrobeSlot.SHOES)
                : List.of(WardrobeSlot.INNER_TOP, WardrobeSlot.BOTTOM, WardrobeSlot.SHOES);
        for (WardrobeSlot slot : required) {
            List<WardrobeItem> options = ranked.stream().filter(item -> item.getSlot() == slot && !selected.containsValue(item)).toList();
            if (!selected.containsKey(slot) && !options.isEmpty()) selected.put(slot, options.get(alternativeIndex % options.size()));
        }
        if (!dress && !selected.containsKey(WardrobeSlot.OUTER_TOP)) {
            ranked.stream().filter(item -> item.getSlot() == WardrobeSlot.OUTER_TOP).findFirst().ifPresent(item -> selected.put(item.getSlot(), item));
        }
        List<String> ids = new ArrayList<>(selected.values().stream().map(WardrobeItem::getId).toList());
        String context = scene.contains("面试") ? "正式感与亲和力" : scene.contains("海边") ? "透气度与轻松氛围" : "场景、风格与舒适度";
        return new AiProposal("场景适配方案", ids, "优先平衡" + context + "，并只使用当前衣橱中的真实单品。");
    }

    private int score(WardrobeItem item, String scene) {
        int score = 0;
        for (String tag : item.getSceneTags()) if (scene.contains(tag)) score += 30;
        for (String tag : item.getStyleTags()) if (scene.contains(tag)) score += 20;
        if (!"UNKNOWN".equals(item.getMaterial())) score += 5;
        return score;
    }

    @Override public String modelName() { return "mock-deterministic-v1"; }
}
