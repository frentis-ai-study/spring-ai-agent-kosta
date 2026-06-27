package com.kosta.demo.pdfrag.web;

import com.kosta.demo.pdfrag.ingest.IngestManifest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * RAG 질의 처리: 인덱싱 문서가 없으면 안내, 있으면 LLM 답변 + 근거(파일·페이지)를 반환한다.
 */
@Service
public class RagQueryService {

    private static final String NO_DOCS_GUIDE =
            "먼저 PDF 파일을 업로드해 주세요. 업로드된 문서를 근거로 답변해 드립니다.";

    public record Source(String filename, Integer page) {}

    public record AnswerResult(String content, List<Source> sources) {}

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final IngestManifest manifest;

    public RagQueryService(ChatClient pdfRagChatClient, VectorStore vectorStore, IngestManifest manifest) {
        this.chatClient = pdfRagChatClient;
        this.vectorStore = vectorStore;
        this.manifest = manifest;
    }

    public AnswerResult answer(String message) {
        if (manifest.isEmpty()) {
            return new AnswerResult(NO_DOCS_GUIDE, List.of());
        }

        // QuestionAnswerAdvisor(기본 어드바이저)가 검색→증강→생성을 수행한다.
        String content = chatClient.prompt()
                .user(message)
                .call()
                .content();

        return new AnswerResult(content, retrieveSources(message));
    }

    /** 근거 표시용 별도 검색(동일 파라미터). filename+page 중복 제거. */
    private List<Source> retrieveSources(String message) {
        List<Document> hits = vectorStore.similaritySearch(SearchRequest.builder()
                .query(message)
                .topK(4)
                .similarityThreshold(0.3)
                .build());
        if (hits == null) return List.of();

        Set<String> seen = new LinkedHashSet<>();
        List<Source> sources = new ArrayList<>();
        for (Document d : hits) {
            String filename = asString(d.getMetadata().get("filename"));
            Integer page = asInteger(d.getMetadata().get("page_number"));
            String key = filename + "#" + page;
            if (seen.add(key)) {
                sources.add(new Source(filename, page));
            }
        }
        return sources;
    }

    private String asString(Object v) {
        return v == null ? null : v.toString();
    }

    private Integer asInteger(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v == null) return null;
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
