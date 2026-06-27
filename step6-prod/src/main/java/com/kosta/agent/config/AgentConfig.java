package com.kosta.agent.config;

import com.kosta.agent.advisor.ModerationInputAdvisor;
import com.kosta.agent.tool.CatalogTools;
import com.kosta.agent.tool.OrderTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * step6: 운영용 Agent ChatClient.
 * step5(Memory + RAG + Tools + SafeGuard + Logger)에 ModerationInputAdvisor를 선두로 추가한다.
 */
@Configuration
public class AgentConfig {

    private static final String SYSTEM_PROMPT = """
            당신은 KOSTA 쇼핑몰의 AI 고객 상담사입니다.
            - 항상 한국어 격식체로 답합니다.
            - 다음 의도는 반드시 해당 도구(tool)를 호출해 처리합니다. 임의로 거절하거나 추측하지 마십시오.
              - 상품 재고·가격 문의 → checkInventory
              - 배송 진행 상황·도착 시점 문의 → trackShipment
              - 반품·환불 접수 요청 → requestRefund (도구 반환값에 따라 상태별로 안내)
              - 주문 취소 → cancelOrder
              - 주문 상태 조회 → getOrderStatus, 고객·주문 목록 → findCustomer·getRecentOrders
            - 반품 "규정", 배송비, 멤버십 같은 정책·FAQ "설명"은 검색된 문서(RAG)를 근거로 답합니다.
              (반품을 실제로 "접수"하는 요청은 위 requestRefund 도구로 처리합니다.)
            - 취소·반품 등 변경 작업은 도구의 반환 결과를 그대로 신뢰해 안내하고, 상태를 임의로 단정하지 않습니다.
            - 근거가 부족하면 모른다고 답하고 임의로 추측하지 않습니다.
            - 비속어, 위해 콘텐츠 요청은 정중히 거절합니다.
            """;

    /**
     * 파일 기반 SimpleVectorStore.
     * - 외부 벡터 DB(예: pgvector) 없이 로컬 파일에 임베딩을 영속화한다
     * - 운영에서는 application.yml과 의존성만 바꿔 pgvector·Redis·Supabase 등으로 무손실 전환 가능
     */
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        java.io.File persistFile = new java.io.File("./data/vector-store.json");
        if (persistFile.exists()) {
            store.load(persistFile);
        }
        return store;
    }

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
                // 정책 문서에 없는 질문(재고·배송 등)도 Tool Calling으로 처리되도록 빈 컨텍스트를 허용한다.
                // 기본값(false)이면 관련 문서가 없을 때 "답변 불가"로 단락시켜 도구 호출을 막는다.
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(true)
                        .build())
                .build();
    }

    @Bean
    public ModerationInputAdvisor moderationInputAdvisor() {
        // order=Integer.MIN_VALUE+1000 → 가장 먼저 실행되어 차단되면 LLM 호출 자체를 단락
        return new ModerationInputAdvisor(Integer.MIN_VALUE + 1000);
    }

    @Bean
    public ChatClient agentChatClient(
            ChatClient.Builder builder,
            ChatMemory chatMemory,
            RetrievalAugmentationAdvisor ragAdvisor,
            ModerationInputAdvisor moderationAdvisor,
            OrderTools orderTools,
            CatalogTools catalogTools) {

        SafeGuardAdvisor safeGuard = SafeGuardAdvisor.builder()
                .sensitiveWords(List.of("주민등록번호", "신용카드번호", "비밀번호"))
                .build();

        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        moderationAdvisor,
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        ragAdvisor,
                        safeGuard,
                        new SimpleLoggerAdvisor()
                )
                .defaultTools(orderTools, catalogTools)
                .build();
    }
}
