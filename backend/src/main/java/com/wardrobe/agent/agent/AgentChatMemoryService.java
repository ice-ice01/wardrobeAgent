package com.wardrobe.agent.agent;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
/**
 * 在业务消息表和 Spring AI ChatMemory 之间建立桥梁。
 *
 * <p>业务表保存完整、可审计的聊天记录；ChatMemory 保存模型下一轮需要看到的
 * 滑动窗口。当 ChatMemory 尚未初始化时，本服务会从业务表恢复最近消息。</p>
 */
public class AgentChatMemoryService {
    private final ChatMemory chatMemory;
    private final MessageRepository messages;
    private final int maxMessages;

    public AgentChatMemoryService(ChatMemory chatMemory, MessageRepository messages,
                                  @Value("${app.ai.memory.max-messages:20}") int maxMessages) {
        this.chatMemory = chatMemory;
        this.messages = messages;
        this.maxMessages = maxMessages;
    }

    /**
     * 返回当前用户会话对应的记忆 ID，并在记忆为空时从业务消息表恢复上下文。
     */
    public String initialize(String userId, String conversationId) {
        String memoryId = memoryId(userId, conversationId);
        if (!chatMemory.get(memoryId).isEmpty()) return memoryId;

        // 查询结果按时间倒序返回，反转后才能按真实对话顺序交给模型。
        List<Message> recent = new ArrayList<>(messages.findRecentCompleted(
                conversationId, userId, PageRequest.of(0, maxMessages)));
        Collections.reverse(recent);

        List<org.springframework.ai.chat.messages.Message> initialMemory = recent.stream()
                .map(this::toAiMessage)
                .filter(Objects::nonNull)
                .toList();
        // 避免窗口从孤立的 Assistant 消息开始，模型上下文从第一条 User 消息起步。
        int firstUser = 0;
        while (firstUser < initialMemory.size() && !(initialMemory.get(firstUser) instanceof UserMessage)) firstUser++;
        if (firstUser < initialMemory.size()) chatMemory.add(memoryId, initialMemory.subList(firstUser, initialMemory.size()));
        return memoryId;
    }

    /** 使用用户 ID 和会话 ID 生成稳定且相互隔离的记忆键。 */
    public static String memoryId(String userId, String conversationId) {
        String scope = "wardrobe-agent:" + userId + ":" + conversationId;
        return UUID.nameUUIDFromBytes(scope.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** 将业务层角色转换为 Spring AI 能识别的消息角色。 */
    private org.springframework.ai.chat.messages.Message toAiMessage(Message message) {
        return switch (message.getRole()) {
            case "USER" -> new UserMessage(message.getContent());
            case "ASSISTANT" -> new AssistantMessage(message.getContent());
            default -> null;
        };
    }
}
