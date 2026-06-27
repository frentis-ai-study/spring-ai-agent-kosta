package com.kosta.demo.pdfrag.web;

import com.kosta.demo.pdfrag.ingest.IngestManifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RagQueryServiceTest {

    private ChatClient chatClient;
    private VectorStore vectorStore;
    private IngestManifest manifest;
    private RagQueryService service;

    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callSpec;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        vectorStore = mock(VectorStore.class);
        manifest = mock(IngestManifest.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        service = new RagQueryService(chatClient, vectorStore, manifest);
    }

    @Test
    void 인덱싱된_문서가_없으면_업로드_안내를_반환하고_LLM을_호출하지_않는다() {
        when(manifest.isEmpty()).thenReturn(true);

        RagQueryService.AnswerResult result = service.answer("질문");

        assertThat(result.content()).contains("PDF");
        assertThat(result.sources()).isEmpty();
        verify(chatClient, never()).prompt();
    }

    @Test
    void 문서가_있으면_LLM_답변과_근거를_반환한다() {
        when(manifest.isEmpty()).thenReturn(false);
        when(callSpec.content()).thenReturn("문서에 따르면 환불은 7일 이내 가능합니다.");
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new Document("환불 규정 ...", Map.of("filename", "policy.pdf", "page_number", 2)),
                new Document("환불 규정 계속 ...", Map.of("filename", "policy.pdf", "page_number", 2))
        ));

        RagQueryService.AnswerResult result = service.answer("환불 며칠 이내 가능?");

        assertThat(result.content()).contains("환불");
        // 동일 (filename, page) 는 중복 제거
        assertThat(result.sources()).hasSize(1);
        assertThat(result.sources().get(0).filename()).isEqualTo("policy.pdf");
        assertThat(result.sources().get(0).page()).isEqualTo(2);
    }
}
