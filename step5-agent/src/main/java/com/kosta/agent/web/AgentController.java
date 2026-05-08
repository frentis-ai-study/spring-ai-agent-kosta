package com.kosta.agent.web;

import com.kosta.agent.rag.PolicyIndexer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;

/**
 * 종합 Agent 엔드포인트.
 * - POST /api/agent : 단발 호출 (Memory + RAG + Tools + SafeGuard 적용)
 * - GET  /api/agent/stream : SSE 스트리밍 (timeout, doOnCancel 포함)
 * - POST /api/index : RAG 정책 문서 인덱싱
 */
@RestController
@RequestMapping("/api")
public class AgentController {

    private final ChatClient agent;
    private final PolicyIndexer indexer;

    public AgentController(ChatClient agentChatClient, PolicyIndexer indexer) {
        this.agent = agentChatClient;
        this.indexer = indexer;
    }

    @PostMapping("/agent")
    public Map<String, String> call(@RequestBody ChatRequest req) {
        String content = agent.prompt()
                .user(req.message())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId(req)))
                .call()
                .content();
        return Map.of("conversationId", conversationId(req), "content", content);
    }

    @GetMapping(value = "/agent/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestParam String message,
                               @RequestParam(required = false) String conversationId) {
        String cid = conversationId == null ? "default" : conversationId;
        return agent.prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, cid))
                .stream()
                .content()
                .timeout(Duration.ofSeconds(60))
                .doOnCancel(() -> System.out.println("[stream] client cancelled cid=" + cid));
    }

    @PostMapping("/index")
    public Map<String, Object> index() {
        int chunks = indexer.indexAll();
        return Map.of("status", "ok", "chunks", chunks);
    }

    private String conversationId(ChatRequest req) {
        return (req.conversationId() == null || req.conversationId().isBlank())
                ? "default" : req.conversationId();
    }

    public record ChatRequest(String message, String conversationId) {}
}
