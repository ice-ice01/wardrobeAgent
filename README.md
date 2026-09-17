# 拾搭 Wardrobe Agent

状态：可运行 MVP  
版本：1.7.5  
最后更新时间：2026-08-29

这是基于同一份 PRD 实现的 AI 智能衣橱前后端项目。后端负责鉴权、衣橱检索、AI 结构化搭配、Java 硬规则校验与异步试穿；前端提供可完成整条业务链路的桌面和移动端界面。

## 项目结构

| 路径 | 内容 |
| --- | --- |
| `backend/` | Java 21、Spring Boot 4.1.1、Spring AI 2.0.1、MySQL、Flyway |
| `frontend/` | React、TypeScript、Vite、Vitest、MSW、Playwright、Lucide Icons |
| `backend/src/main/resources/db/migration/` | MySQL 表结构迁移 |
| `backend/src/main/resources/static/assets/seed/` | 118 张本地化商品实拍图、3 张预设形象图和 1 张 Mock 试穿图 |
| `backend/scripts/` | 建库、素材生成与后端启动脚本 |

产品与工程事实来源见 [产品文档索引](../01产品设计相关文档/README.md)。

## 已实现功能

- JWT 登录与用户数据隔离。
- 衣物图片安全上传、格式校验、重编码、鉴权回显和衣橱 CRUD。
- 118 件可追溯的公开商品目录实拍素材，其中新增的 100 件按 20/15/20/15/20/10 覆盖上装、外套、下装、连衣裙、鞋履和配饰。
- Spring AI Tool Calling 会话 Agent：普通问答、日期工具、JDBC 持久化的完整轮次消息窗口，以及按需触发的衣橱推荐；方案卡片按推荐轮次紧随对应的助手回复。
- 对话改为持久化 `agent_run` 后台任务：提交接口先返回运行 ID，模型正文与工具事件落库并通过 SSE 推送；切页、刷新或关闭浏览器不会取消运行，返回后可恢复当前会话和未完成生成。
- “换一套”绑定来源方案，保留场景与锁定商品，并由 Java 强制避免重复组合。
- Spring AI OpenAI-compatible 接入；模型只负责候选选择，Java 负责所有权、槽位冲突、完整性和版本校验。
- 会话 Agent 使用 JDBC 持久化的 `ChatMemory` 窗口；网关显式读写记忆，优先使用 `stream().content()` 逐片发送正文形成打字机效果，流式正文为空时再用 `call().content()` 兼容只返回 tool-call 帧的供应商，避免影响既有工具循环和记忆修复。
- 大衣橱使用 Spring AI `EmbeddingModel` 语义召回，再按 slot 和 MMR 控制多样性；锁定/排除、用户隔离和最终真实性仍由 MySQL/Java 保证。
- 穿搭阶段使用 Spring AI 多模态 `UserMessage`：后端读取本地商品图、生成受控缩略图并随候选文本发送，让模型同时参考实际颜色、轮廓、版型和图案。
- 搭配收藏、确认和历史记录。
- 预设形象、授权上传、自定义默认形象。
- 供应商无关 `TryOnProvider`、Mock/FASHN/GPT Image Edit/Spring AI Image 适配器、一次性确认令牌、额度/并发限制和阶段级状态持久化。
- 试穿任务通过 `AFTER_COMMIT` 事务事件启动异步 Worker，避免阶段记录尚未提交时被误判为 `TRYON_INCOMPLETE`。
- 试穿 Worker 使用数据库租约原子抢占，应用启动及每 5 秒扫描遗漏/过期任务；已保存 Provider 任务 ID 的阶段恢复查询，提交连接中断则进入 `SUBMISSION_UNKNOWN`，避免自动重复付费请求。
- 试穿页默认勾选方案中全部受支持商品，可取消单件；鞋履/配饰禁用说明，后端按内搭→下装→外搭串行生成并如实展示部分成功。
- Spring AI `ImageModel` 可直接作为主 Provider，也可在 FASHN 可降级技术故障时接管；任务和逐件阶段保存实际 Provider、兜底原因和 `AI_PREVIEW` 类型，前端明确提示其不是高保真试穿。审核、安全、非法输入和授权错误不会兜底。
- GPT Image Edit 复用共享 AI Key，通过原生 `/v1/images/edits` 同时上传人物图和当前商品图；多件衣物按阶段以上一次结果继续编辑，结果明确标记为 `AI_PREVIEW`。兼容网关返回 Base64 别名或临时图片 URL，后端会立即转码并持久化结果。
- 30 条离线 AI 评测集、运行/明细落库，以及 JSON、HTML、JUnit XML 报告；Mock 硬门禁已接入 CI。
- Vitest + MSW 单元/API 测试、桌面/移动 Playwright 主流程、GitHub Actions 镜像构建与报告上传。
- MySQL、Spring Boot、React/Nginx 的 Docker Compose 编排、健康检查、命名卷和默认零 Key Mock 模式。
- 响应式 React 界面：搭配助手、衣橱、形象、方案与试穿记录；发送中即时回显用户消息，按 Agent 工具事件动态展示处理阶段，并逐片追加模型正文形成打字机效果。
- 前端对话和试穿统一使用带 JWT 的 Fetch SSE，支持 `Last-Event-ID`、1/2/5 秒断线重连和 3 秒轮询降级；已移除试穿弹窗 700ms 固定轮询。
- 后端 SSE 广播会在完成、超时或断线时淘汰订阅者，断线异常不进入通用 500 错误处理；前端订阅只绑定任务 ID，不因运行状态变化反复断开，并在连续失败后才提示、恢复后自动清除提示。

## 环境要求

- Java 21
- Node.js 20+
- 本机 MySQL 8.x
- 环境变量 `WARDROBE_DB_USERNAME`、`WARDROBE_DB_PASSWORD`
- 真实 AI 模式还需要 `AI_API_KEY`、`AI_BASE_URL`、`AI_MODEL`
- 真实试穿需要 `TRYON_PROVIDER=FASHN_V1_6`、`TRYON_REAL_ENABLED=true`、`FASHN_API_KEY`
- 本地直接启动默认使用 `TRYON_PROVIDER=GPT_IMAGE_EDIT` 和 `TRYON_REAL_ENABLED=true`；每阶段向 `/v1/images/edits` 同时提交当前人物图与当前商品图
- GPT Image 双图编辑复用 `AI_API_KEY`、`AI_BASE_URL` 和 `AI_IMAGE_MODEL`；`SPRING_AI_IMAGE` 仍可显式配置为文字生图主路径或 FASHN 技术故障兜底

`AI_BASE_URL` 填供应商域名根地址，应用默认用 `${AI_BASE_URL}/v1` 调用 Chat Completions。若供应商的 Chat API 根路径不是该形式，可额外设置 `AI_CHAT_BASE_URL` 为完整地址。

对话工具执行默认开启；如需排查供应商是否支持工具调用，可设置 `AI_TOOL_CALLING_ENABLED=false` 临时关闭自动工具执行。关闭后只能得到普通文本回答，穿搭和日期工具不会执行。

多模态默认开启，要求 `AI_MODEL` 和兼容供应商支持图片输入。可使用 `AI_MULTIMODAL_ENABLED=false` 临时回退到纯文本推荐，或通过 `AI_MULTIMODAL_MAX_IMAGES` 调整每轮最多附加的商品图数量（默认 30）。无需 CDN：后端向 Spring AI 提供本地 `Resource`，SDK 在模型请求中编码为 Base64；Base64 不进入数据库和前端。

Embedding 默认使用 `AI_BASE_URL` 同一兼容供应商和 `text-embedding-3-small`。当前开发机已使用硅基流动 OpenAI-compatible Embeddings 验证 `BAAI/bge-m3`（1024 维）：`AI_EMBEDDING_BASE_URL=https://api.siliconflow.cn/v1`、`AI_EMBEDDING_MODEL=BAAI/bge-m3`、`AI_EMBEDDING_INDEX_VERSION=wardrobe-text-v1-bge-m3`。Key 只保存在 Windows 用户环境变量 `AI_EMBEDDING_API_KEY`，不写入仓库。如果供应商的根路径、密钥或模型不同，可分别覆盖这些变量。主要可调配置如下：

| 环境变量 | 默认值 | 作用 |
| --- | --- | --- |
| `AI_SEMANTIC_RETRIEVAL_ENABLED` | `true` | 是否开启语义召回 |
| `AI_RETRIEVAL_WARDROBE_THRESHOLD` | `30` | 超过该 eligible 数量才启用 Embedding |
| `AI_RETRIEVAL_INITIAL_RECALL` | `60` | 初始语义召回数 |
| `AI_RETRIEVAL_CANDIDATES_PER_SLOT` | `4` | MMR 后每个 slot 保留数 |
| `AI_RETRIEVAL_RELEVANCE_WEIGHT` | `0.7` | MMR 相关性权重 |
| `AI_RETRIEVAL_DIVERSITY_WEIGHT` | `0.3` | MMR 相似性惩罚权重 |
| `AI_RETRIEVAL_RECENT_ITEM_PENALTY` | `0.25` | 最近展示商品降权 |
| `AI_EMBEDDING_INDEX_VERSION` | `wardrobe-text-v1` | 索引文本/模型迁移版本 |
| `AI_RETRIEVAL_FALLBACK_ENABLED` | `true` | Embedding 失败时是否回退旧候选池 |

查询向量不保存。衣物向量保存在 MySQL `wardrobe_item_embedding`，衣物修改时按内容哈希更新，删除时同步删除。修改索引文本或 Embedding 模型时应同时提高 `AI_EMBEDDING_INDEX_VERSION`；运行时发现向量维度不同也会自动重建。

如果当前聊天兼容供应商不提供 Embeddings，需要为上述变量配置独立兼容服务。`start-backend.ps1` 会从 Windows 用户环境读取独立 Embedding 配置；未配置或服务失败时，超过阈值的衣橱会触发可观测的回退策略，不会中断生成搭配。

会话短期记忆默认保留最多 20 条消息，并按完整用户轮次淘汰；可通过 `AI_MEMORY_MAX_MESSAGES` 调整。`message` 表保存页面所需的完整历史，`spring_ai_chat_memory` 只保存送给模型的上下文窗口。两者职责不同，不应手工删除其中一张表来“同步”另一张表。

Agent 的 NDJSON 异步请求默认允许执行 2 分钟，以覆盖多模态图片准备、模型调用和短暂重试；可通过 `AI_REQUEST_TIMEOUT` 使用 Spring Duration 格式调整，例如 `90s` 或 `3m`。对话 Agent 优先使用 Spring AI `.stream().content()`，每个正文片段都作为 `message.delta` 事件发送，前端据此呈现打字机效果；若兼容供应商的流式工具调用只返回 tool-call 帧而没有正文，则回退到 `.call().content()` 完成同一轮工具循环。穿搭工具内部的结构化 `AiProposal` 仍使用 `.call().entity()` 等待完整 JSON 后再执行 Java 校验。该超时值应大于最慢的一次完整 Agent 运行及重试所需时间。

FASHN 主链路与 Spring AI 兜底可以这样启动：

```powershell
$env:FASHN_API_KEY = '<fashn-key>'
$env:AI_API_KEY = '<shared-ai-key>'
$env:AI_BASE_URL = 'https://api.openai.com'
$env:AI_MODEL = 'gpt-4o-mini'
$env:AI_IMAGE_MODEL = 'gpt-image-1'
.\backend\scripts\start-backend.ps1 -AiMode live -TryOnProvider FASHN_V1_6 -EnableSpringAiFallback
```

Spring AI `ImageModel` 与聊天模型共享供应商 Key 和 Base URL，但使用独立的 `AI_IMAGE_MODEL`。共享 Key 必须拥有 Images API 权限，供应商也必须同时兼容聊天与生图接口。当前兜底走通用图片生成，不能保证保持本人身份、指定服装图案或版型，因此结果只显示为“AI 效果预览”。默认关闭兜底；FASHN 图片处理或 Spring AI 图片服务调用均需要用户在创建任务时明确确认。

不使用 FASHN、直接调用 Spring AI 图片模型时：

```powershell
$env:AI_API_KEY = '<shared-ai-key>'
$env:AI_BASE_URL = '<openai-compatible-root-url>'
$env:AI_MODEL = '<chat-model>'
$env:AI_IMAGE_MODEL = 'gpt-image-1'
.\backend\scripts\start-backend.ps1 -AiMode live -TryOnProvider SPRING_AI_IMAGE
```

直连结果同样只显示为“AI 效果预览”，`fallbackUsed=false`。当前通用 Images Generate 调用依据受控穿搭文字描述生成图片，不保证保持人物身份或原服装图片细节。

使用人物图和商品图进行 GPT Image 双图编辑时：

```powershell
$env:AI_API_KEY = '<shared-ai-key>'
$env:AI_BASE_URL = '<openai-compatible-root-url>'
$env:AI_MODEL = '<chat-model>'
$env:AI_IMAGE_MODEL = 'gpt-image-1'
.\backend\scripts\start-backend.ps1 -AiMode live -TryOnProvider GPT_IMAGE_EDIT
```

该模式调用 `${AI_BASE_URL}/v1/images/edits`，每个阶段都发送当前人物/上一阶段结果图和当前衣物图。当前网关已用项目 Seed 图片真实返回 Base64 PNG；该结果仍是通用图片编辑预览，不等同于专用虚拟试穿保真承诺。

当前机器已经创建 `wardrobe_agent` 数据库。换到新环境时，可先执行：

```powershell
cd backend
.\scripts\init-mysql.ps1
```

## 启动

终端 1，启动真实 AI 后端：

```powershell
cd D:\Desktop\AI全栈衣橱项目\wardrobeAgent\backend
.\scripts\start-backend.ps1 -AiMode live
```

无需调用模型、只体验稳定业务流程时使用：

```powershell
.\scripts\start-backend.ps1 -AiMode mock -Offline
```

脚本还支持 `-ServerPort 8082` 与 `-AllowedOrigins http://localhost:5174`，用于并行启动隔离开发实例。前端可通过 `VITE_BACKEND_TARGET` 指向非 8080 后端。

终端 2，启动前端：

```powershell
cd D:\Desktop\AI全栈衣橱项目\wardrobeAgent\frontend
npm install
npm run dev
```

打开 `http://localhost:5173`。演示账号：`demo` / `wardrobe123`。

安装 Docker 的机器也可从项目根目录一条命令启动默认 Mock 环境：

```powershell
Copy-Item .env.example .env
docker compose up --build
```

打开 `http://localhost:8088`。真实密钥只能放入本机 `.env` 或部署平台 Secret，不能提交到仓库。

## 演示商品素材

商品实拍图已随项目本地化，正常启动不依赖外部图片站点。需要从公开数据集重新同步素材时执行：

```powershell
cd D:\Desktop\AI全栈衣橱项目\wardrobeAgent\backend
.\scripts\sync-public-catalog.ps1
```

脚本扫描公开 Fashion Product Images 数据集并按固定 slot 配额选取 100 件商品，将图片统一为 `720 x 900` JPEG，并更新 `static/assets/seed/public-catalog-manifest.json`。清单保留源商品 ID、结构化属性、来源页面与 MIT 许可；运行中的前后端不会实时抓取或热链第三方图片。原 18 件目录仍可用 `sync-catalog-assets.ps1` 复现。

## 验证命令

```powershell
.\mvnw.cmd test

cd ..\frontend
npm test
npm run build
npm run test:e2e
```

已在 MySQL 8.4.11 上验证 Flyway v1-v7 迁移、JDBC Chat Memory、持久化 Agent Run、任务租约、阶段试穿与兜底来源字段、衣物向量表及 Hibernate 7 模型校验。当前 35 项后端测试、30/30 AI 评测、8 项 Vitest/MSW 测试和桌面/移动 4 项 Playwright 测试通过；新增测试覆盖默认图片 Provider、持久化对话后台完成、SSE 鉴权终态、真实断线写失败隔离、临时断线重连和试穿提交状态不明保护。

## 当前边界

- FASHN 适配器与分阶段编排已经接入，但当前没有使用真实 Key 和授权人像执行付费冒烟；真实画质、层叠保真度、额度消耗与供应商限流仍需单独验收。
- GPT Image `/v1/images/edits` 已使用共享 Key 和两张项目 Seed 图片完成真实冒烟；Spring AI 通用 Images Generate 仍只做文字预览。二者都不应作为专用虚拟试穿画质证据。
- 当前开发机未安装 Docker CLI，因此 Dockerfile、Compose 与 CI 已完成静态配置和普通构建门禁，但未在本机执行 `docker compose up`。
- 演示目录来自 MIT 公开数据集的本地快照，不提供实时价格、库存或购买链接；若接入真实电商目录，需要单独处理供应商 API、数据授权和更新机制。
- 多模态推荐会增加视觉 Token、延迟和模型费用；当前采用最大 `512 x 640` JPEG 缩略图和默认 30 张上限，不等同于自动商品标签识别。
- 当前不引入独立向量数据库，余弦相似度和 MMR 由应用扫描当前用户向量计算，适用于单用户衣橱规模；数千至上万件级别需评估 Spring AI `VectorStore` 和 ANN 服务。
- MVP 为单机单体部署思路；生产环境需要替换 JWT 默认密钥、配置对象存储、限流、可观测性和备份策略。
