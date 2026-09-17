package com.wardrobe.agent.outfit;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/** 方案商品快照仓库，按 layerOrder 恢复稳定展示顺序。 */
public interface OutfitPlanItemRepository extends JpaRepository<OutfitPlanItem, String> {
    List<OutfitPlanItem> findAllByPlanIdOrderByLayerOrderAsc(String planId);
}
