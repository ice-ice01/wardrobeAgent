# Wardrobe Agent 学习路线

这份指南给出推荐阅读顺序。代码中的注释解释局部机制，这里负责把模块串成完整请求链路。

## 1. 先看普通 Spring 请求

从 `auth/AuthController` 和 `auth/AuthService` 开始，理解：

- `@RestController` 如何接收 JSON；
- `@Valid` 如何触发参数校验；
- 构造器注入如何获得 Service；
- `GlobalExceptionHandler` 如何统一返回错误；
- JWT Filter 如何把用户放入 `SecurityContext`。

然后阅读 `wardrobe/WardrobeController`、`WardrobeService`、`WardrobeItemRepository` 和
`WardrobeItem`，理解 Controller -> Service -> Repository -> Entity 的基本分层。

## 2. 阅读一轮 Agent 消息

入口是 `agent/AgentController.message()`：

```text
HTTP 请求
-> AgentService.process()
-> ConversationAgentGateway.respond()
-> ChatClient
-> AgentEvent
-> StreamingResponseBody
-> 前端按 NDJSON 行读取
```

对话 Agent 优先使用 `.stream().content()`，每个正文片段都会立即转换为一个 `message.delta` NDJSON
事件；前端按事件追加 `content`，形成打字机效果。流结束后才把完整回答写入 Chat Memory，
`message.completed` 到达后再替换临时轮次。部分 OpenAI-compatible 服务的流式工具调用只返回
tool-call 帧，导致正文流为空；此时网关保留 `.call().content()` 同步兜底，不重复执行工具结果。

## 3. 理解对话记忆

依次阅读：

1. `AgentMemoryConfig`
2. `AgentChatMemoryService`
3. `SpringConversationAgentGateway`
4. `Message` 与 `MessageRepository`

`message` 表保存完整业务历史；`SPRING_AI_CHAT_MEMORY` 保存最近 20 条模型上下文。
模型本身无状态，每一轮由 `SpringConversationAgentGateway` 从 `ChatMemory` 读取历史并放进 Prompt，再把完整助手回答写回 JDBC 记忆。Spring AI 2.0.1 的流式 Tool Calling 聚合分支可能丢失 Advisor 上下文，因此该路径不再依赖 `MessageChatMemoryAdvisor` 自动写回；这不是移除记忆，而是把同一 ChatMemory 的读写时机移到网关中。

## 4. 理解 Tool Calling

`SpringConversationAgentGateway` 使用 `.tools(tools)` 把 `WardrobeAgentTools` 暴露给模型。
模型只决定是否调用和提供参数，真正权限检查、查询和写库仍在 `AgentService.generateOutfit()`。

普通问答只经过对话模型；穿搭请求会进入工具，并再调用 `AiGateway` 完成软性搭配选择。

## 5. 理解结构化输出和多模态

阅读 `SpringAiGateway`、`AiProposal` 和 `WardrobeVisionService`：

- `.user(...).param(...)` 构造 Prompt 模板；
- `.media(...)` 添加衣物图片；
- `.call().entity(AiProposal.class)` 把完整模型输出转成 Java record；它与对话正文流式输出用途不同，不能把半截 JSON 直接落库；
- `AgentService.normalizeSelection()` 不信任模型 ID，重新执行白名单和冲突校验。

## 6. 理解 Embedding 检索

依次阅读：

1. `WardrobeEmbeddingIndexer`
2. `JdbcWardrobeEmbeddingStore`
3. `WardrobeSemanticRetriever`
4. `WardrobeRetrievalProperties`

流程是衣物文本 -> Embedding 向量 -> MySQL BLOB -> Java 余弦相似度 -> MMR 多样性重排。
当前实现适合 MVP；它会加载用户向量在 Java 中比较，不是专用向量数据库的 ANN 检索。

## 7. 区分 AI 与 Mock

- `SpringConversationAgentGateway`、`SpringAiGateway`：真实模型模式；
- `MockConversationAgentGateway`、`MockAiGateway`：确定性测试替身；
- `TryOnService`、`TryOnWorker`：统一编排 Mock、FASHN `tryon-v1.6` 与 Spring AI `ImageModel` 低保真兜底；实际 Provider 和结果类型由状态机持久化。

阅读时始终使用这个边界：模型负责语言和软性判断，数据库、权限、状态机和硬规则由 Java 负责。
