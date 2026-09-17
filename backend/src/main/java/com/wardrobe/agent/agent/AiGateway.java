package com.wardrobe.agent.agent;

import com.wardrobe.agent.wardrobe.WardrobeItem;
import java.util.List;
import java.util.Set;

/**
 * “软性搭配模型”的抽象边界。它只提出候选组合，不能绕过 Java 权限和硬规则校验。
 */
public interface AiGateway {
    /** 根据场景、候选商品和历史组合返回结构化搭配建议。 */
    AiProposal propose(String scene, List<WardrobeItem> candidates, Set<String> lockedItemIds,
                       int alternativeIndex, Set<String> previousCombinationKeys);

    /** 返回实际或模拟模型名称。 */
    String modelName();
}
