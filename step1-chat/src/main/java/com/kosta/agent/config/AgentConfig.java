package com.kosta.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * step1: 가장 단순한 ChatClient. 메모리/도구/RAG 없음.
 */
@Configuration
public class AgentConfig {

    private static final String SYSTEM_PROMPT = """
            당신은 KOSTA 쇼핑몰의 친절한 상담사입니다.
            - 항상 한국어 격식체로 답합니다.
            """;

    @Bean
    public ChatClient agentChatClient(ChatClient.Builder builder) {
        return builder.defaultSystem(SYSTEM_PROMPT).build();
    }
}
