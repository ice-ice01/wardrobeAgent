package com.wardrobe.agent.agent;

import com.wardrobe.agent.common.BusinessException;
import com.wardrobe.agent.common.PrefixIds;
import com.wardrobe.agent.outfit.OutfitPlan;
import com.wardrobe.agent.outfit.OutfitPlanItem;
import com.wardrobe.agent.outfit.OutfitPlanItemRepository;
import com.wardrobe.agent.outfit.OutfitPlanRepository;
import com.wardrobe.agent.outfit.OutfitService;
import com.wardrobe.agent.outfit.OutfitView;
import com.wardrobe.agent.retrieval.WardrobeRetrievalRequest;
import com.wardrobe.agent.retrieval.WardrobeRetrievalResult;
import com.wardrobe.agent.retrieval.WardrobeSemanticRetriever;
import com.wardrobe.agent.user.AppUser;
import com.wardrobe.agent.user.AppUserRepository;
import com.wardrobe.agent.wardrobe.WardrobeItem;
import com.wardrobe.agent.wardrobe.WardrobeItemRepository;
import com.wardrobe.agent.wardrobe.WardrobeSlot;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Service
/**
 * Agent 模块的应用服务和总编排器。
 *
 * <p>它串联会话记忆、对话 Agent、工具调用、语义检索、搭配模型、Java 硬规则、
 * 数据持久化和 NDJSON 事件。模型负责软判断，权限与业务正确性始终由本类保证。</p>
 */
public class AgentService {
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final RecommendationRunRepository runs;
    private final WardrobeItemRepository wardrobe;
    private final AppUserRepository users;
    private final OutfitPlanRepository plans;
    private final OutfitPlanItemRepository planItems;
    private final OutfitService outfitService;
    private final AiGateway ai;
    private final ConversationAgentGateway conversationAgent;
    private final AgentChatMemoryService chatMemory;
    private final WardrobeSemanticRetriever semanticRetriever;

    public AgentService(ConversationRepository conversations, MessageRepository messages, RecommendationRunRepository runs,
                        WardrobeItemRepository wardrobe, AppUserRepository users, OutfitPlanRepository plans,
                        OutfitPlanItemRepository planItems, OutfitService outfitService, AiGateway ai,
                        ConversationAgentGateway conversationAgent, AgentChatMemoryService chatMemory,
                        WardrobeSemanticRetriever semanticRetriever) {
        this.conversations = conversations; this.messages = messages; this.runs = runs; this.wardrobe = wardrobe;
        this.users = users; this.plans = plans; this.planItems = planItems; this.outfitService = outfitService; this.ai = ai;
        this.conversationAgent = conversationAgent;
        this.chatMemory = chatMemory;
        this.semanticRetriever = semanticRetriever;
    }

    @Transactional
    /** 创建属于当前用户的新会话，并返回包含初始空消息列表的视图。 */
    public ConversationView createConversation(String userId, String title) {
        Conversation saved = conversations.save(new Conversation(userId, title == null || title.isBlank() ? "新对话" : title.trim()));
        return view(saved, userId);
    }

    @Transactional(readOnly = true)
    /** 只返回会话摘要，避免列表接口加载每个会话的全部消息和搭配。 */
    public List<ConversationView> list(String userId) {
        return conversations.findAllByUserIdOrderByLastMessageAtDesc(userId).stream().map(c -> summary(c)).toList();
    }

    @Transactional(readOnly = true)
    public ConversationView get(String userId, String id) { return view(requireConversation(userId, id), userId); }

    @Transactional
    /**
     * 处理一轮用户消息。方法返回 void，处理进度通过 output 回调逐个输出 AgentEvent。
     * StreamingResponseBody 会把这些事件立即写成 NDJSON；这不等于模型 Token 流。
     */
    public void process(String userId, String conversationId, AgentMessageRequest request, Consumer<AgentEvent> output) {
        Conversation conversation = requireConversation(userId, conversationId);
        long[] sequence = {0};

        // emit 统一补齐事件 ID、会话 ID、序号和时间，再交给 Controller 提供的 output。
        Consumer<EventData> emit = event -> output.accept(new AgentEvent(PrefixIds.next("evt"), event.type(), conversationId,
                ++sequence[0], java.time.Instant.now(), event.data()));
        emit.accept(new EventData("message.started", Map.of("clientMessageId", request.clientMessageId())));

        // clientMessageId 提供幂等性：前端重试时直接重放已完成回答，不重复调用模型和创建方案。
        var replay = messages.findByUserIdAndClientMessageIdAndRole(userId, request.clientMessageId(), "ASSISTANT");
        if (replay.isPresent()) {
            emit.accept(new EventData("message.delta", Map.of("content", replay.get().getContent(), "replayed", true)));
            emit.accept(new EventData("message.completed", Map.of("messageId", replay.get().getId(),
                    "content", replay.get().getContent(), "replayed", true)));
            return;
        }

        String memoryId = chatMemory.initialize(userId, conversationId);
        // 业务消息表用于审计；ChatMemory 是另一个只面向模型上下文的滑动窗口。
        messages.save(new Message(userId, conversationId, request.clientMessageId(), "USER", request.content().trim(), "COMPLETED"));
        Message assistant = messages.save(new Message(userId, conversationId, request.clientMessageId(), "ASSISTANT", "", "PROCESSING"));
        conversation.touch(request.content().trim());
        // Lambda 把模型工具调用连接到当前用户、当前会话的受信任业务方法。
        WardrobeAgentTools tools = new WardrobeAgentTools(scene -> generateOutfit(
                userId, conversationId, request, scene, emit));
        String text = conversationAgent.respond(memoryId, request.content(), request.action(), tools,
                delta -> emit.accept(new EventData("message.delta", Map.of("content", delta))));
        // JPA 管理的 assistant 实体会在事务提交时通过脏检查写回完整回答。
        assistant.complete(text, conversationAgent.modelName());
        if (tools.outfitResult() != null && tools.outfitResult().outfit() != null) {
            emit.accept(new EventData("outfit.created", tools.outfitResult().outfit()));
            emit.accept(new EventData("message.completed", Map.of("messageId", assistant.getId(),
                    "content", text, "runId", tools.outfitResult().outfit().recommendationRunId())));
        } else {
            emit.accept(new EventData("message.completed", Map.of("messageId", assistant.getId(), "content", text)));
        }
    }

    /**
     * generateOutfit 工具的真实实现：检索候选、调用软性搭配模型、校验、持久化并返回 DTO。
     */
    private WardrobeAgentTools.OutfitToolResult generateOutfit(String userId, String conversationId,
                                                               AgentMessageRequest request, String requestedScene,
                                                               Consumer<EventData> emit) {
        OutfitPlan sourcePlan = sourcePlan(userId, conversationId, request);
        String scene = sourcePlan == null ? safeText(requestedScene, request.content(), 500) : sourcePlan.getScene();
        if (needsWeatherClarification(scene)) {
            return new WardrobeAgentTools.OutfitToolResult("NEEDS_CLARIFICATION",
                    "这个场景会明显受到天气影响。请补充具体地点，或直接告诉我温度、降雨和风力情况，我不会猜测实时天气。", null);
        }

        List<WardrobeItem> all = wardrobe.findAllByUserIdAndDeletedFalseOrderByCreatedAtAsc(userId);
        Map<String, WardrobeItem> byId = all.stream().collect(java.util.stream.Collectors.toMap(WardrobeItem::getId, item -> item));
        Set<String> locked = new LinkedHashSet<>(request.safeLocked());
        if (sourcePlan != null) planItems.findAllByPlanIdOrderByLayerOrderAsc(sourcePlan.getId()).stream()
                .filter(OutfitPlanItem::isLocked).map(OutfitPlanItem::getItemId).forEach(locked::add);
        validateConstraints(locked, request.safeExcluded(), byId);
        List<WardrobeItem> eligible = all.stream().filter(item -> !request.safeExcluded().contains(item.getId())).toList();
        List<OutfitPlan> previousPlans = plans.findAllByConversationIdAndUserIdOrderByCreatedAtAsc(conversationId, userId);
        Set<String> recentlyShownItemIds = recentlyShownItemIds(previousPlans);
        // 检索层只做高召回候选缩减，不直接决定最终穿搭。
        WardrobeRetrievalResult retrieval = semanticRetriever.retrieve(new WardrobeRetrievalRequest(
                userId, scene, eligible, locked, request.safeExcluded(), recentlyShownItemIds,
                "EXPLORE_MORE".equalsIgnoreCase(request.action())));
        List<WardrobeItem> candidates = retrieval.candidates();
        AppUser user = users.findById(userId).orElseThrow();
        RecommendationRun run = runs.save(new RecommendationRun(userId, conversationId, request.clientMessageId(), scene,
                user.getWardrobeRevision(), candidates.stream().map(WardrobeItem::getId).collect(java.util.stream.Collectors.toSet()),
                locked, request.safeExcluded()));

        emit.accept(new EventData("tool.started", Map.of("tool", "searchWardrobe")));
        run.status("RETRIEVING");
        emit.accept(new EventData("tool.completed", Map.of(
                "tool", "searchWardrobe",
                "eligibleTotal", eligible.size(),
                "semanticRecallCount", retrieval.semanticRecallCount(),
                "consideredCount", candidates.size(),
                "slotCounts", retrieval.slotCounts(),
                "elapsedMs", retrieval.elapsedMillis(),
                "fallback", retrieval.fallback(),
                "embeddingModel", retrieval.model(),
                "indexVersion", retrieval.indexVersion())));
        run.status("COMPOSING");
        Set<String> previousCombinationKeys = previousPlans.stream().map(this::combinationKey).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        int alternativeIndex = "EXPLORE_MORE".equalsIgnoreCase(request.action()) ? previousPlans.size() : 0;
        // AiProposal 是不可信的 AI 中间结果，下面必须再执行 Java 白名单和冲突校验。
        AiProposal proposal = ai.propose(scene, candidates, locked, alternativeIndex, previousCombinationKeys);

        emit.accept(new EventData("tool.started", Map.of("tool", "evaluateOutfit")));
        run.status("VALIDATING");
        List<WardrobeItem> selected = normalizeSelection(proposal, candidates, locked, scene, alternativeIndex);
        if ("EXPLORE_MORE".equalsIgnoreCase(request.action())) {
            selected = ensureNovelSelection(selected, candidates, locked, previousCombinationKeys, alternativeIndex);
        }
        Set<String> missing = missingSlots(selected);
        boolean complete = missing.isEmpty();
        String exploration = candidates.size() < eligible.size() ? "PARTIALLY_CONSIDERED" : "ALL_ITEMS_CONSIDERED";
        // 只有通过硬规则后的结果才会转换成 OutfitPlan 和 OutfitPlanItem 持久化。
        OutfitPlan plan = plans.save(new OutfitPlan(userId, conversationId, run.getId(),
                safeText(proposal.title(), "场景适配方案", 120), scene, complete, missing,
                safeText(proposal.reason(), "根据当前衣橱候选生成，并通过 Java 硬规则校验。", 1000), exploration,
                all.size(), eligible.size(), candidates.size(), candidates.size() < eligible.size(), user.getWardrobeRevision()));
        int layer = 0;
        for (WardrobeItem item : selected) planItems.save(new OutfitPlanItem(plan.getId(), item, locked.contains(item.getId()), layer++));
        run.shown(combinationKey(selected));
        run.status("COMPLETED");
        emit.accept(new EventData("tool.completed", Map.of("tool", "evaluateOutfit", "complete", complete, "missingSlots", missing)));
        OutfitView planView = outfitService.view(plan);
        String message = complete ? "已从你的真实衣橱生成方案：" + plan.getReason()
                : "当前衣橱还缺少 " + String.join("、", missing) + "。已保留可用的次优组合，没有虚构不存在的商品。";
        return new WardrobeAgentTools.OutfitToolResult("CREATED", message, planView);
    }

    /** “换一套”时确定来源方案，并验证它确实属于当前会话。 */
    private OutfitPlan sourcePlan(String userId, String conversationId, AgentMessageRequest request) {
        if (!"EXPLORE_MORE".equalsIgnoreCase(request.action())) return null;
        OutfitPlan source = request.sourceOutfitId() == null || request.sourceOutfitId().isBlank()
                ? plans.findAllByConversationIdAndUserIdOrderByCreatedAtAsc(conversationId, userId).stream().reduce((first, second) -> second).orElse(null)
                : outfitService.require(userId, request.sourceOutfitId());
        if (source != null && !conversationId.equals(source.getConversationId())) {
            throw new BusinessException(HttpStatus.CONFLICT, "OUTFIT_CONVERSATION_MISMATCH", "来源方案不属于当前会话");
        }
        return source;
    }

    /**
     * 将 AI 选择限制在候选白名单中，保留锁定商品、拒绝位置冲突，并补齐基础位置。
     */
    static List<WardrobeItem> normalizeSelection(AiProposal proposal, List<WardrobeItem> candidates, Set<String> locked,
                                                 String scene, int alternativeIndex) {
        Map<String, WardrobeItem> allowed = candidates.stream().collect(java.util.stream.Collectors.toMap(WardrobeItem::getId, item -> item));
        LinkedHashSet<WardrobeItem> result = new LinkedHashSet<>();
        locked.forEach(id -> { if (allowed.containsKey(id)) result.add(allowed.get(id)); });
        if (proposal != null && proposal.selectedItemIds() != null) proposal.selectedItemIds().forEach(id -> {
            WardrobeItem item = allowed.get(id); if (item != null && canAdd(result, item)) result.add(item);
        });
        boolean hasDress = result.stream().anyMatch(item -> item.getSlot() == WardrobeSlot.DRESS);
        List<WardrobeSlot> needed = hasDress ? List.of(WardrobeSlot.DRESS, WardrobeSlot.SHOES)
                : List.of(WardrobeSlot.INNER_TOP, WardrobeSlot.BOTTOM, WardrobeSlot.SHOES);
        for (WardrobeSlot slot : needed) {
            if (result.stream().noneMatch(item -> item.getSlot() == slot)) {
                List<WardrobeItem> options = candidates.stream().filter(item -> item.getSlot() == slot && canAdd(result, item))
                        .toList();
                if (!options.isEmpty()) result.add(options.get(alternativeIndex % options.size()));
            }
        }
        return new ArrayList<>(result);
    }

    /** 通过组合键检测历史重复，并尝试替换一个未锁定位置来生成真正的新组合。 */
    private List<WardrobeItem> ensureNovelSelection(List<WardrobeItem> selected, List<WardrobeItem> candidates,
                                                    Set<String> locked, Set<String> previousKeys, int alternativeIndex) {
        if (!previousKeys.contains(combinationKey(selected))) return selected;
        List<WardrobeItem> current = new ArrayList<>(selected);
        for (int index = current.size() - 1; index >= 0; index--) {
            WardrobeItem existing = current.get(index);
            if (locked.contains(existing.getId())) continue;
            List<WardrobeItem> replacements = candidates.stream()
                    .filter(candidate -> candidate.getSlot() == existing.getSlot())
                    .filter(candidate -> current.stream().noneMatch(item -> item.getId().equals(candidate.getId())))
                    .toList();
            for (int offset = 0; offset < replacements.size(); offset++) {
                List<WardrobeItem> alternative = new ArrayList<>(current);
                alternative.set(index, replacements.get((alternativeIndex + offset) % replacements.size()));
                if (!previousKeys.contains(combinationKey(alternative))) return alternative;
            }
        }
        throw new BusinessException(HttpStatus.CONFLICT, "NO_MORE_OUTFITS", "当前约束下没有更多不重复的穿搭组合");
    }

    private String combinationKey(OutfitPlan plan) {
        return planItems.findAllByPlanIdOrderByLayerOrderAsc(plan.getId()).stream().map(OutfitPlanItem::getItemId)
                .sorted().reduce((first, second) -> first + ":" + second).orElse("empty");
    }

    /** 组合键忽略商品顺序，只要 itemId 集合相同就视为同一套搭配。 */
    private String combinationKey(List<WardrobeItem> selected) {
        return selected.stream().map(WardrobeItem::getId).sorted().reduce((first, second) -> first + ":" + second).orElse("empty");
    }

    /** 保证一个 slot 最多一件，并禁止连衣裙与上下装分件同时出现。 */
    private static boolean canAdd(Set<WardrobeItem> current, WardrobeItem candidate) {
        if (current.stream().anyMatch(item -> item.getSlot() == candidate.getSlot())) return false;
        boolean dress = current.stream().anyMatch(item -> item.getSlot() == WardrobeSlot.DRESS) || candidate.getSlot() == WardrobeSlot.DRESS;
        boolean separates = current.stream().anyMatch(item -> item.getSlot() == WardrobeSlot.INNER_TOP || item.getSlot() == WardrobeSlot.BOTTOM)
                || candidate.getSlot() == WardrobeSlot.INNER_TOP || candidate.getSlot() == WardrobeSlot.BOTTOM;
        return !(dress && separates);
    }

    /** 根据“连衣裙路线”或“上下装路线”计算方案还缺哪些必需位置。 */
    private Set<String> missingSlots(List<WardrobeItem> selected) {
        Set<WardrobeSlot> slots = selected.stream().map(WardrobeItem::getSlot).collect(java.util.stream.Collectors.toSet());
        LinkedHashSet<String> missing = new LinkedHashSet<>();
        if (slots.contains(WardrobeSlot.DRESS)) { if (!slots.contains(WardrobeSlot.SHOES)) missing.add("SHOES"); }
        else {
            if (!slots.contains(WardrobeSlot.INNER_TOP)) missing.add("INNER_TOP");
            if (!slots.contains(WardrobeSlot.BOTTOM)) missing.add("BOTTOM");
            if (!slots.contains(WardrobeSlot.SHOES)) missing.add("SHOES");
        }
        return missing;
    }

    /** 最近三套方案参与检索降权，避免用户连续看到同一批商品。 */
    private Set<String> recentlyShownItemIds(List<OutfitPlan> previousPlans) {
        int fromIndex = Math.max(0, previousPlans.size() - 3);
        return previousPlans.subList(fromIndex, previousPlans.size()).stream()
                .flatMap(plan -> planItems.findAllByPlanIdOrderByLayerOrderAsc(plan.getId()).stream())
                .map(OutfitPlanItem::getItemId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /** 在模型调用前验证锁定/排除集合、商品所有权和 slot 冲突。 */
    private void validateConstraints(Set<String> lockedIds, Set<String> excludedIds, Map<String, WardrobeItem> items) {
        if (!java.util.Collections.disjoint(lockedIds, excludedIds))
            throw new BusinessException(HttpStatus.CONFLICT, "ITEM_CONFLICT", "同一商品不能同时锁定和排除");
        List<WardrobeItem> locked = lockedIds.stream().map(id -> {
            WardrobeItem item = items.get(id);
            if (item == null) throw new BusinessException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "指定商品不存在或不属于当前用户");
            return item;
        }).toList();
        Set<WardrobeSlot> seen = new HashSet<>();
        for (WardrobeItem item : locked) if (!seen.add(item.getSlot()))
            throw new BusinessException(HttpStatus.CONFLICT, "ITEM_CONFLICT", "两件指定商品位置冲突，请分别生成方案或调整选择");
    }

    /** 缺少地点或天气事实时要求澄清，避免模型伪造实时天气。 */
    private boolean needsWeatherClarification(String text) {
        boolean weatherScene = text.contains("海边") || text.contains("明天") || text.contains("下雨") || text.contains("户外");
        boolean supplied = text.matches(".*(北京|上海|广州|深圳|三亚|青岛|厦门|杭州|成都|武汉|[零一二三四五六七八九十0-9]+度|晴|雨|雪|大风).*?");
        return weatherScene && !supplied;
    }

    private Conversation requireConversation(String userId, String id) {
        return conversations.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", "会话不存在"));
    }

    private ConversationView view(Conversation conversation, String userId) {
//
        Map<String, String> runIdsByClientMessage = runs.findAllByConversationIdAndUserIdOrderByCreatedAtAsc(conversation.getId(), userId).stream()
                .collect(java.util.stream.Collectors.toMap(RecommendationRun::getClientMessageId, RecommendationRun::getId, (first, ignored) -> first));
        // 详情接口读取完整业务历史；这和只保留固定窗口的 ChatMemory 用途不同。
        List<ConversationView.MessageView> messageViews = messages.findAllByConversationIdAndUserIdOrderByCreatedAtAsc(conversation.getId(), userId).stream()
                .map(message -> new ConversationView.MessageView(message.getId(), message.getClientMessageId(),
                        runIdsByClientMessage.get(message.getClientMessageId()), message.getRole(), message.getContent(),
                        message.getStatus(), message.getCreatedAt())).toList();
        List<OutfitView> outfitViews = plans.findAllByConversationIdAndUserIdOrderByCreatedAtAsc(conversation.getId(), userId).stream().map(outfitService::view).toList();
        return new ConversationView(conversation.getId(), conversation.getTitle(), conversation.getLastMessageAt(), messageViews, outfitViews);
    }

    private ConversationView summary(Conversation conversation) {
        return new ConversationView(conversation.getId(), conversation.getTitle(), conversation.getLastMessageAt(), List.of(), List.of());
    }

    private String safeText(String value, String fallback, int max) {
        String text = value == null || value.isBlank() ? fallback : value.trim(); return text.length() > max ? text.substring(0, max) : text;
    }
    private record EventData(String type, Object data) {}
}
