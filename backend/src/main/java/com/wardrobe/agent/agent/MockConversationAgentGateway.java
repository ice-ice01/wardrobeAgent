package com.wardrobe.agent.agent;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "mock")
/**
 * 不访问外部模型的确定性对话实现，供本地开发和集成测试使用。
 * 它手工模拟记忆读取、工具路由和回复写回，验证业务链路而非模型能力。
 */
public class MockConversationAgentGateway implements ConversationAgentGateway {
    private final ChatMemory chatMemory;

    public MockConversationAgentGateway(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    @Override
    /** 使用简单关键词代替模型意图判断，并显式维护与 live 模式相同的 ChatMemory。 */
    public String respond(String memoryId, String content, String action, WardrobeAgentTools tools,
                          Consumer<String> onDelta) {
        var history = chatMemory.get(memoryId);
        chatMemory.add(memoryId, new UserMessage(content));
        String response;
        if ("EXPLORE_MORE".equalsIgnoreCase(action) || requestsOutfit(content)) {
            response = tools.generateOutfit(content).message();
        } else if (content.matches(".*(今天|现在|当前).*(几月|几号|几点|日期|时间|星期).*")) {
            response = "当前时间是 " + tools.getCurrentDateTime() + "。";
        } else if (content.contains("刚才") || content.contains("之前")) {
            String previous = history.stream().filter(message -> "USER".equals(message.getMessageType().name()))
                    .reduce((first, second) -> second)
                    .map(org.springframework.ai.chat.messages.Message::getText)
                    .orElse("没有更早的用户消息");
            response = "你之前说的是：“" + previous + "”。";
        } else if (content.contains("什么模型")) {
            response = "当前会话由 " + modelName() + " 驱动。";
        } else {
            response = "我可以正常回答，也可以在你明确提出穿搭需求时查询真实衣橱生成方案。";
        }
        chatMemory.add(memoryId, new AssistantMessage(response));
        emitChunks(response, onDelta);
        return response;
    }

    /** 固定按 Unicode 码点拆片，让离线测试也覆盖多个增量事件和中文字符边界。 */
    private void emitChunks(String response, Consumer<String> onDelta) {
        int[] codePoints = response.codePoints().toArray();
        for (int start = 0; start < codePoints.length; start += 8) {
            int length = Math.min(8, codePoints.length - start);
            onDelta.accept(new String(codePoints, start, length));
        }
    }

    private boolean requestsOutfit(String content) {
        return content.matches(".*(穿搭|怎么穿|搭一套|搭配|换一套|换双|换件|面试|婚礼|约会|通勤|聚餐|看展|海边).*?");
    }

    @Override public String modelName() { return "mock-agent-v2"; }
}
