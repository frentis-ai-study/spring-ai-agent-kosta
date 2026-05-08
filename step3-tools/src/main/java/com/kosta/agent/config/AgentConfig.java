package com.kosta.agent.config;

import com.kosta.agent.tool.OrderTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * step3: ChatClient + Memory + Tool Calling.
 * RAG, SafeGuard, Logger는 step4/step5에서 추가합니다.
 */
@Configuration
public class AgentConfig {

    private static final String SYSTEM_PROMPT = """
            당신은 KOSTA 쇼핑몰의 주문 처리 도우미입니다.
            - 항상 한국어 격식체로 답합니다.
            - 고객 정보, 주문 상태, 취소 등은 반드시 제공된 도구(tool)를 호출해 확인합니다.
            - 도구 호출 시 호출자 ID(callerCustomerId)를 항상 함께 전달합니다.
            - 사용자가 본인의 주문/고객 정보를 자연어로 요청하면 즉시 findCustomer 또는 getRecentOrders 도구를 호출하여 답변하십시오.
            - 정책/환불/반품 관련 질문은 검색된 문서 내용을 기반으로 답변하십시오.
            """;

    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(10)
                .build();
    }

    @Bean
    public ChatClient agentChatClient(
            ChatClient.Builder builder,
            ChatMemory chatMemory,
            OrderTools orderTools) {

        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(orderTools)
                .build();
    }
}
