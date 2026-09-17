package com.wardrobe.agent.outfit;

import com.wardrobe.agent.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
/** 管理搭配方案状态并把 Plan + PlanItem 实体聚合为前端 OutfitView。 */
public class OutfitService {
    private final OutfitPlanRepository plans;
    private final OutfitPlanItemRepository planItems;
    public OutfitService(OutfitPlanRepository plans, OutfitPlanItemRepository planItems) { this.plans = plans; this.planItems = planItems; }

    @Transactional(readOnly = true)
    public List<OutfitView> list(String userId) { return plans.findAllByUserIdOrderByCreatedAtDesc(userId).stream().map(this::view).toList(); }
    @Transactional(readOnly = true)
    public OutfitView get(String userId, String id) { return view(require(userId, id)); }
    @Transactional
    public OutfitView save(String userId, String id) { OutfitPlan plan = require(userId, id); plan.savePlan(); return view(plan); }
    @Transactional
    /** 不完整方案不能确认，避免下游试穿得到缺少核心位置的输入。 */
    public OutfitView confirm(String userId, String id) {
        OutfitPlan plan = require(userId, id);
        if (!plan.isComplete()) throw new BusinessException(HttpStatus.CONFLICT, "OUTFIT_INCOMPLETE", "不完整方案不能确认试穿");
        plan.confirm(); return view(plan);
    }
    /** 使用 userId 限定查询，避免访问其他用户的方案。 */
    public OutfitPlan require(String userId, String id) {
        return plans.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "OUTFIT_NOT_FOUND", "穿搭方案不存在"));
    }
    /** 从方案主表和商品快照子表组装稳定的 API DTO。 */
    public OutfitView view(OutfitPlan plan) {
        List<OutfitView.Item> items = planItems.findAllByPlanIdOrderByLayerOrderAsc(plan.getId()).stream()
                .map(item -> new OutfitView.Item(item.getItemId(), item.getSlot(), item.getItemName(), item.getImageUrl(), item.isLocked())).toList();
        return new OutfitView(plan.getId(), plan.getRecommendationRunId(), plan.getTitle(), plan.getScene(), plan.getPlanVersion(), plan.getStatus(), plan.isComplete(),
                plan.getMissingSlots(), plan.getReason(), plan.getExplorationStatus(), plan.getWardrobeTotal(), plan.getEligibleTotal(),
                plan.getConsideredCount(), plan.isHasMore(), items, plan.getCreatedAt());
    }
}
