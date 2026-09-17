package com.wardrobe.agent.agent;

import java.util.function.Consumer;

/**
 * 对话 Agent 的模型边界。业务层只依赖该接口，因此 live 模型和 mock 实现可以互换。
 */
public interface ConversationAgentGateway {
    /** 结合对话记忆和本轮工具生成回答，并把模型正文片段按到达顺序交给 onDelta。 */
    String respond(String memoryId, String content, String action, WardrobeAgentTools tools,
                   Consumer<String> onDelta);

    /** 返回当前实现使用的模型标识，供消息审计和前端展示。 */
    String modelName();
}
