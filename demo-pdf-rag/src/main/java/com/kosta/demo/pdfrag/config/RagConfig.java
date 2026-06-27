package com.kosta.demo.pdfrag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * PDF SimpleRAG 챗봇의 핵심 빈.
 * - VectorStore: 파일 영속 SimpleVectorStore (부팅 시 기존 인덱스 로드)
 * - ChatClient: 시스템 프롬프트 + QuestionAnswerAdvisor(RAG) 기본 등록
 */
@Configuration
public class RagConfig {

    private static final String SYSTEM_PROMPT = """
            당신은 업로드된 PDF 문서를 근거로 답하는 AI 문서 비서입니다.
            - 항상 한국어 격식체로 답합니다.
            - 검색되어 제공된 문서 내용만을 근거로 답합니다.
            - 근거가 부족하면 "제공된 문서에서 해당 내용을 찾을 수 없습니다."라고 답하고 임의로 추측하지 않습니다.
            - 가능하면 답변 끝에 근거가 된 내용을 간단히 요약해 덧붙입니다.
            """;

    @Bean
    public VectorStore vectorStore(
            EmbeddingModel embeddingModel,
            @Value("${agent.rag.persist-path:./data/vector-store.json}") String persistPath) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        File persistFile = new File(persistPath);
        if (persistFile.exists()) {
            store.load(persistFile);
        }
        return store;
    }

    @Bean
    public ChatClient pdfRagChatClient(ChatClient.Builder builder, VectorStore vectorStore) {
        QuestionAnswerAdvisor qaAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .topK(4)
                        .similarityThreshold(0.3)
                        .build())
                .build();
        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(qaAdvisor)
                .build();
    }
}
