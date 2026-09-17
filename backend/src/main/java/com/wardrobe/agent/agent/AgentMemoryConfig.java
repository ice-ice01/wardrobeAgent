package com.wardrobe.agent.agent;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
/**
 * 配置 Spring AI 的短期对话记忆。
 *
 * <p>{@link MessageWindowChatMemory} 只保留最近若干条消息，底层
 * {@link ChatMemoryRepository} 由 JDBC Starter 提供，因此记忆可以跨应用重启保存。</p>
 */
public class AgentMemoryConfig {

    @Bean
    ChatMemory agentChatMemory(ChatMemoryRepository repository,
                               @Value("${app.ai.memory.max-messages:20}") int maxMessages) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(maxMessages)
                .build();
    }

}
