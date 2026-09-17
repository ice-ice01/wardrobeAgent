package com.wardrobe.agent;

import com.wardrobe.agent.agent.AgentChatMemoryService;
import com.wardrobe.agent.agent.WardrobeVisionService;
import com.wardrobe.agent.user.AppUserRepository;
import com.wardrobe.agent.wardrobe.WardrobeItemRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.time.Duration;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 端到端接口测试：启动完整 Spring 容器，并通过 MockMvc 模拟前端发送 HTTP 请求。
 *
 * <p>这里使用 test profile 中的可控 AI 实现，因此不访问真实模型，但仍会经过认证、
 * 会话记忆、工具调用、搭配生成、NDJSON 流式响应和试穿任务等真实业务链路。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired WardrobeVisionService vision;
    @Autowired WardrobeItemRepository wardrobeItems;
    @Autowired AppUserRepository users;
    @Autowired JdbcTemplate jdbc;

    @Test
    /** 验证送给多模态模型的衣物图片已经被统一压缩、转码并保持商品顺序。 */
    void controlledWardrobeImagesArePreparedForMultimodalPrompt() throws Exception {
        String userId = users.findByUsername("demo").orElseThrow().getId();
        var candidates = wardrobeItems.findAllByUserIdAndDeletedFalseOrderByCreatedAtAsc(userId);
        List<WardrobeVisionService.PromptImage> images = vision.prepare(candidates);

        assertThat(candidates).hasSizeGreaterThanOrEqualTo(15);
        assertThat(images).hasSize(Math.min(30, candidates.size()));
        assertThat(images).extracting(WardrobeVisionService.PromptImage::itemId)
                .containsExactlyElementsOf(candidates.stream().limit(30).map(item -> item.getId()).toList());
        for (int index = 0; index < images.size(); index++) {
            var promptImage = images.get(index);
            assertThat(promptImage.ordinal()).isEqualTo(index + 1);
            assertThat(promptImage.mimeType().toString()).isEqualTo("image/jpeg");
            assertThat(promptImage.byteSize()).isPositive();
            try (var input = promptImage.resource().getInputStream()) {
                var decoded = ImageIO.read(input);
                assertThat(decoded).isNotNull();
                assertThat(decoded.getWidth()).isLessThanOrEqualTo(512);
                assertThat(decoded.getHeight()).isLessThanOrEqualTo(640);
            }
        }
    }

    @Test
    /** 验证教学商品目录成功导入 100 件商品，并覆盖系统定义的每一种穿搭槽位。 */
    void publicTeachingCatalogContainsOneHundredItemsAndCoversEverySlot() {
        String userId = users.findByUsername("demo").orElseThrow().getId();
        var catalog = wardrobeItems.findAllByUserIdAndDeletedFalseOrderByCreatedAtAsc(userId).stream()
                .filter(item -> item.getImageUrl().matches("/assets/seed/catalog-[0-9]{3}\\.jpg"))
                .toList();

        assertThat(catalog).hasSize(100);
        var countsBySlot = catalog.stream().collect(Collectors.groupingBy(item -> item.getSlot(), Collectors.counting()));
        assertThat(countsBySlot).hasSize(6);
        assertThat(countsBySlot.values()).allMatch(count -> count >= 10);
    }

    @Test
    /** 验证 JWT 安全规则：业务接口需要登录，合法 token 可以读取当前用户。 */
    void protectsBusinessApisAndAuthenticatesDemoUser() throws Exception {
        mvc.perform(get("/api/wardrobe/items")).andExpect(status().isUnauthorized());
        String token = login();
        mvc.perform(get("/api/users/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("demo"));
    }

    @Test
    /** 验证模型只能在候选衣物中搭配，并且用户锁定的衣物必须保留。 */
    void seedWardrobeIsAvailableAndAgentKeepsLockedItem() throws Exception {
        String token = login();
        JsonNode wardrobe = body(mvc.perform(get("/api/wardrobe/items").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn());
        assertThat(wardrobe.size()).isGreaterThanOrEqualTo(15);
        String lockedId = null;
        for (JsonNode item : wardrobe) if ("炭灰修身西装".equals(item.path("name").asString())) lockedId = item.path("id").asString();
        assertThat(lockedId).isNotBlank();

        String conversationId = createConversation(token);
        String clientMessageId = UUID.randomUUID().toString();
        JsonNode stream = sendMessage(token, conversationId, Map.of(
                "clientMessageId", clientMessageId,
                "content", "参加朋友婚礼，希望正式但不要太商务",
                "lockedItemIds", new String[]{lockedId}
        ));
        String outfitLine = findEvent(stream, "outfit.created");
        assertThat(outfitLine).contains(lockedId).contains("PARTIALLY_CONSIDERED");
        JsonNode createdOutfit = json.readTree(outfitLine).path("data");
        assertThat(createdOutfit.path("hasMore").asBoolean()).isTrue();
        assertThat(createdOutfit.path("consideredCount").asInt()).isLessThan(createdOutfit.path("eligibleTotal").asInt());
        JsonNode conversation = body(mvc.perform(get("/api/agent/conversations/{id}", conversationId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn());
        JsonNode assistantMessage = null;
        for (JsonNode message : conversation.path("messages")) {
            if ("ASSISTANT".equals(message.path("role").asString())
                    && clientMessageId.equals(message.path("clientMessageId").asString())) assistantMessage = message;
        }
        assertThat(assistantMessage).isNotNull();
        assertThat(assistantMessage.path("recommendationRunId").asString()).isEqualTo(createdOutfit.path("recommendationRunId").asString());
        assertThat(conversation.path("outfits").get(0).path("recommendationRunId").asString())
                .isEqualTo(assistantMessage.path("recommendationRunId").asString());
    }

    @Test
    /** 验证确认搭配后能够创建异步试穿任务，并最终得到 Mock Provider 的结果。 */
    void confirmedPlanCreatesRecoverableMockTryOnTask() throws Exception {
        String token = login();
        String conversationId = createConversation(token);
        JsonNode stream = sendMessage(token, conversationId, Map.of(
                "clientMessageId", UUID.randomUUID().toString(),
                "content", "去互联网公司面试，正式但不要显得老气"
        ));
        JsonNode outfitEvent = json.readTree(findEvent(stream, "outfit.created"));
        String planId = outfitEvent.path("data").path("id").asString();
        int version = outfitEvent.path("data").path("version").asInt();
        mvc.perform(post("/api/outfits/{id}/confirm", planId).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));

        JsonNode models = body(mvc.perform(get("/api/user-models").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn());
        String modelId = models.get(0).path("id").asString();
        JsonNode capabilities = body(mvc.perform(get("/api/tryon/capabilities")
                        .queryParam("planId", planId).queryParam("planVersion", String.valueOf(version))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn());
        List<String> selectedItemIds = new java.util.ArrayList<>();
        for (JsonNode item : capabilities.path("supportedItems")) {
            if (!"DRESS".equals(item.path("slot").asString()) && selectedItemIds.size() < 2) {
                selectedItemIds.add(item.path("itemId").asString());
            }
        }
        if (selectedItemIds.isEmpty() && !capabilities.path("supportedItems").isEmpty()) {
            selectedItemIds.add(capabilities.path("supportedItems").get(0).path("itemId").asString());
        }
        assertThat(selectedItemIds).isNotEmpty();
        JsonNode confirmation = postJson(token, "/api/tryon/confirmations", Map.of(
                "planId", planId, "planVersion", version, "userModelId", modelId, "provider", "MOCK",
                "selectedItemIds", selectedItemIds, "consent", false));
        JsonNode task = postJson(token, "/api/tryon/tasks", Map.of(
                "confirmationToken", confirmation.path("token").asString(),
                "idempotencyKey", UUID.randomUUID().toString(),
                "userModelId", modelId,
                "selectedItemIds", selectedItemIds,
                "simulateFailure", false
        ));
        String taskId = task.path("id").asString();
        assertThat(task.path("status").asString()).isIn("SUBMITTED", "PROCESSING");

        Awaitility.await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            JsonNode current = body(mvc.perform(get("/api/tryon/tasks/{id}", taskId).header("Authorization", bearer(token)))
                    .andExpect(status().isOk()).andReturn());
            assertThat(current.path("status").asString()).isEqualTo("SUCCEEDED");
            assertThat(current.path("resultImageUrl").asString()).isNotBlank();
            assertThat(current.path("renderedItemIds").size()).isEqualTo(selectedItemIds.size());
            assertThat(current.path("items")).anyMatch(item -> "SUCCEEDED".equals(item.path("status").asString()));
        });
    }

    @Test
    /**
     * 验证普通聊天会写入 Spring AI ChatMemory，后续问题能读取上下文，
     * 同时窗口大小受限且不同 conversation 之间不会串话。
     */
    void ordinaryConversationUsesMemoryWithoutCreatingOutfit() throws Exception {
        String token = login();
        String conversationId = createConversation(token);
        JsonNode first = sendMessage(token, conversationId, Map.of(
                "clientMessageId", UUID.randomUUID().toString(),
                "content", "我最喜欢绿色"
        ));
        assertThat(hasEvent(first, "outfit.created")).isFalse();

        JsonNode second = sendMessage(token, conversationId, Map.of(
                "clientMessageId", UUID.randomUUID().toString(),
                "content", "我刚才说最喜欢什么颜色"
        ));
        assertThat(messageText(second)).contains("我最喜欢绿色");
        assertThat(hasEvent(second, "outfit.created")).isFalse();

        String userId = users.findByUsername("demo").orElseThrow().getId();
        String memoryId = AgentChatMemoryService.memoryId(userId, conversationId);
        Integer memorySize = jdbc.queryForObject(
                "select count(*) from SPRING_AI_CHAT_MEMORY where conversation_id = ?", Integer.class, memoryId);
        String firstType = jdbc.queryForObject(
                "select type from SPRING_AI_CHAT_MEMORY where conversation_id = ? order by sequence_id limit 1",
                String.class, memoryId);
        assertThat(memorySize).isEqualTo(4);
        assertThat(firstType).isEqualTo("USER");

        sendMessage(token, conversationId, Map.of(
                "clientMessageId", UUID.randomUUID().toString(),
                "content", "你好"
        ));
        Integer boundedMemorySize = jdbc.queryForObject(
                "select count(*) from SPRING_AI_CHAT_MEMORY where conversation_id = ?", Integer.class, memoryId);
        String boundedFirstType = jdbc.queryForObject(
                "select type from SPRING_AI_CHAT_MEMORY where conversation_id = ? order by sequence_id limit 1",
                String.class, memoryId);
        assertThat(boundedMemorySize).isEqualTo(4);
        assertThat(boundedFirstType).isEqualTo("USER");

        String otherConversationId = createConversation(token);
        String otherMemoryId = AgentChatMemoryService.memoryId(userId, otherConversationId);
        Integer otherMemorySize = jdbc.queryForObject(
                "select count(*) from SPRING_AI_CHAT_MEMORY where conversation_id = ?", Integer.class, otherMemoryId);
        assertThat(otherMemorySize).isZero();
    }

    @Test
    /** 浏览器请求只负责创建运行；后台完成后可通过 run 和 conversation 接口恢复最终结果。 */
    void durableAgentRunCompletesAfterSubmissionRequestReturns() throws Exception {
        String token = login();
        String conversationId = createConversation(token);
        String clientMessageId = UUID.randomUUID().toString();
        JsonNode run = body(mvc.perform(post("/api/agent/conversations/{id}/runs", conversationId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of(
                                "clientMessageId", clientMessageId,
                                "content", "你好，这是一个后台对话任务"
                        ))))
                .andExpect(status().isAccepted()).andReturn());
        String runId = run.path("id").asString();

        Awaitility.await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            JsonNode current = body(mvc.perform(get("/api/agent/runs/{id}", runId)
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk()).andReturn());
            assertThat(current.path("status").asString()).isEqualTo("COMPLETED");
            JsonNode conversation = body(mvc.perform(get("/api/agent/conversations/{id}", conversationId)
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk()).andReturn());
            assertThat(conversation.path("messages")).anyMatch(message ->
                    clientMessageId.equals(message.path("clientMessageId").asString())
                            && "ASSISTANT".equals(message.path("role").asString())
                            && "COMPLETED".equals(message.path("status").asString()));
        });
    }

    @Test
    /** 验证日期问题会触发时间工具，而不会错误地触发穿搭生成工具。 */
    void dateQuestionUsesTimeToolWithoutCreatingOutfit() throws Exception {
        String token = login();
        String conversationId = createConversation(token);
        JsonNode stream = sendMessage(token, conversationId, Map.of(
                "clientMessageId", UUID.randomUUID().toString(),
                "content", "今天几月几号"
        ));
        assertThat(messageDeltas(stream)).hasSizeGreaterThan(1);
        assertThat(messageText(stream)).contains("当前时间是").contains("Asia/Shanghai");
        assertThat(hasEvent(stream, "outfit.created")).isFalse();
    }

    @Test
    /** 验证接口只接受后端定义的 AgentAction，不能由客户端注入任意动作指令。 */
    void rejectsUnsupportedAgentAction() throws Exception {
        String token = login();
        String conversationId = createConversation(token);
        mvc.perform(post("/api/agent/conversations/{id}/messages", conversationId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of(
                                "clientMessageId", UUID.randomUUID().toString(),
                                "content", "普通问题",
                                "action", "IGNORE_RULES"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    /** 验证“换一套”会沿用原场景，但通过历史降权得到不同的商品组合。 */
    void exploreMoreCreatesDifferentCombinationFromSourceOutfit() throws Exception {
        String token = login();
        String conversationId = createConversation(token);
        JsonNode firstStream = sendMessage(token, conversationId, Map.of(
                "clientMessageId", UUID.randomUUID().toString(),
                "content", "周末看展，帮我搭一套简洁穿搭"
        ));
        JsonNode first = json.readTree(findEvent(firstStream, "outfit.created")).path("data");

        JsonNode secondStream = sendMessage(token, conversationId, Map.of(
                "clientMessageId", UUID.randomUUID().toString(),
                "content", first.path("scene").asString(),
                "action", "EXPLORE_MORE",
                "sourceOutfitId", first.path("id").asString()
        ));
        JsonNode second = json.readTree(findEvent(secondStream, "outfit.created")).path("data");
        assertThat(itemIds(second)).isNotEqualTo(itemIds(first));
        assertThat(second.path("scene").asString()).isEqualTo(first.path("scene").asString());
    }

    private String login() throws Exception {
        JsonNode response = postJson(null, "/api/auth/login", Map.of("username", "demo", "password", "wardrobe123"));
        return response.path("token").asString();
    }

    private String createConversation(String token) throws Exception {
        return postJson(token, "/api/agent/conversations", Map.of()).path("id").asString();
    }

    private JsonNode sendMessage(String token, String conversationId, Object payload) throws Exception {
        // StreamingResponseBody 会使 MockMvc 请求进入异步状态；先等待完成，再解析每一行 NDJSON 事件。
        MvcResult pending = mvc.perform(post("/api/agent/conversations/{id}/messages", conversationId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(payload)))
                .andExpect(request().asyncStarted()).andReturn();
        MvcResult completed = mvc.perform(asyncDispatch(pending)).andExpect(status().isOk()).andReturn();
        String[] lines = completed.getResponse().getContentAsString().lines().filter(line -> !line.isBlank()).toArray(String[]::new);
        return json.valueToTree(lines);
    }

    private String findEvent(JsonNode lines, String type) throws Exception {
        for (JsonNode line : lines) {
            JsonNode event = json.readTree(line.asString());
            if (type.equals(event.path("type").asString())) return line.asString();
        }
        throw new AssertionError("Missing event " + type + ": " + lines);
    }

    private boolean hasEvent(JsonNode lines, String type) throws Exception {
        for (JsonNode line : lines) if (type.equals(json.readTree(line.asString()).path("type").asString())) return true;
        return false;
    }

    private List<String> messageDeltas(JsonNode lines) throws Exception {
        List<String> deltas = new java.util.ArrayList<>();
        for (JsonNode line : lines) {
            JsonNode event = json.readTree(line.asString());
            if ("message.delta".equals(event.path("type").asString())) {
                deltas.add(event.path("data").path("content").asString());
            }
        }
        return deltas;
    }

    private String messageText(JsonNode lines) throws Exception {
        return String.join("", messageDeltas(lines));
    }

    private Set<String> itemIds(JsonNode outfit) {
        Set<String> ids = new HashSet<>();
        for (JsonNode item : outfit.path("selectedItems")) ids.add(item.path("itemId").asString());
        return ids;
    }

    private JsonNode postJson(String token, String path, Object payload) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(payload));
        if (token != null) request.header("Authorization", bearer(token));
        return body(mvc.perform(request).andExpect(status().is2xxSuccessful()).andReturn());
    }

    private JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsByteArray()); }
    private String bearer(String token) { return "Bearer " + token; }
}
