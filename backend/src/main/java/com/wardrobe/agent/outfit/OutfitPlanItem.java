package com.wardrobe.agent.outfit;

import com.wardrobe.agent.common.AuditedEntity;
import com.wardrobe.agent.wardrobe.WardrobeItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "outfit_plan_item")
/**
 * 方案中的衣物快照。保存名称和图片是为了即使原衣物后来修改或删除，历史方案仍可展示。
 */
public class OutfitPlanItem extends AuditedEntity {
    @Column(nullable = false, length = 40) private String planId;
    @Column(nullable = false, length = 40) private String itemId;
    @Column(nullable = false, length = 20) private String slot;
    @Column(nullable = false, length = 100) private String itemName;
    @Column(nullable = false, length = 500) private String imageUrl;
    @Column(nullable = false) private boolean locked;
    @Column(nullable = false) private int layerOrder;

    protected OutfitPlanItem() {}
    public OutfitPlanItem(String planId, WardrobeItem item, boolean locked, int layerOrder) {
        super("pitem"); this.planId = planId; this.itemId = item.getId(); this.slot = item.getSlot().name();
        this.itemName = item.getName(); this.imageUrl = item.getImageUrl(); this.locked = locked; this.layerOrder = layerOrder;
    }
    public String getPlanId() { return planId; }
    public String getItemId() { return itemId; }
    public String getSlot() { return slot; }
    public String getItemName() { return itemName; }
    public String getImageUrl() { return imageUrl; }
    public boolean isLocked() { return locked; }
    public int getLayerOrder() { return layerOrder; }
}
