package com.kosta.agent.config;

import com.kosta.agent.tool.OrderTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * step4: ChatClient + Memory + Tool Calling + RAG.
 * SafeGuard, Logger 어드바이저는 step5에서 추가합니다.
 */
@Configuration
public class AgentConfig {

    private static final String SYSTEM_PROMPT = """
            당신은 KOSTA 쇼핑몰의 AI 고객 상담사입니다.
            - 항상 한국어 격식체로 답합니다.
            - 고객 정보, 주문 상태, 취소 등은 반드시 제공된 도구(tool)를 호출해 확인합니다.
            - 정책 관련 질문(반품, 배송, FAQ)은 검색된 문서를 근거로만 답합니다.
            - 근거가 부족하면 모른다고 답하고 임의로 추측하지 않습니다.
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
    public RetrievalAugmentationAdvisor retrievalAugmentationAdvisor(VectorStore vectorStore) {
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .vectorStore(vectorStore)
                        // 한국어 임베딩(text-embedding-3-small)에서는 cosine 유사도가 낮게 나오는 경향이 있어 0.3 권장
                        .similarityThreshold(0.3)
                        .topK(4)
                        .build())
                .build();
    }

    @Bean
    public ChatClient agentChatClient(
            ChatClient.Builder builder,
            ChatMemory chatMemory,
            RetrievalAugmentationAdvisor ragAdvisor,
            OrderTools orderTools) {

        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        ragAdvisor
                )
                .defaultTools(orderTools)
                .build();
    }
}
