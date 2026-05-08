package com.kosta.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * step2: ChatClient + Memory(JDBC).
 * conversationId 별로 대화 이력을 유지합니다.
 */
@Configuration
public class AgentConfig {

    private static final String SYSTEM_PROMPT = """
            당신은 KOSTA 쇼핑몰의 친절한 상담사입니다.
            - 항상 한국어 격식체로 답합니다.
            - 이전 대화 맥락을 기억하고 자연스럽게 이어갑니다.
            """;

    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(10)
                .build();
    }

    @Bean
    public ChatClient agentChatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}
