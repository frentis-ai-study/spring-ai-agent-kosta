package com.kosta.agent.web;

import com.kosta.agent.rag.PolicyIndexer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * step6: 운영용 Agent 엔드포인트.
 * - Resilience4j Retry + CircuitBreaker로 OpenAI 호출 안정화
 * - chatResponse()로 finish_reason 검증
 */
@RestController
@RequestMapping("/api")
public class AgentController {

    private final ChatClient agent;
    private final PolicyIndexer indexer;
    private final Retry retry;
    private final CircuitBreaker breaker;

    public AgentController(ChatClient agentChatClient,
                           PolicyIndexer indexer,
                           Retry openAiRetry,
                           CircuitBreaker openAiCircuitBreaker) {
        this.agent = agentChatClient;
        this.indexer = indexer;
        this.retry = openAiRetry;
        this.breaker = openAiCircuitBreaker;
    }

    @PostMapping("/agent")
    public Map<String, Object> call(@RequestBody ChatRequest req) {
        String cid = conversationId(req);

        Supplier<ChatResponse> supplier = () -> agent.prompt()
                .user(req.message())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, cid))
                .call()
                .chatResponse();

        Supplier<ChatResponse> decorated = Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(breaker, supplier));

        ChatResponse response = decorated.get();
        Generation gen = response.getResult();
        ChatGenerationMetadata meta = gen.getMetadata();
        String finishReason = meta != null ? String.valueOf(meta.getFinishReason()) : "unknown";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("conversationId", cid);
        body.put("content", gen.getOutput().getText());
        body.put("finishReason", finishReason);
        // STOP과 STOP_BY_MODERATION(모더레이션 차단)은 정상 종료로 간주
        if (!isNormalFinish(finishReason)) {
            body.put("warning", "비정상 종료. finish_reason 확인 필요.");
        }
        return body;
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

    private boolean isNormalFinish(String finishReason) {
        if (finishReason == null) return false;
        return "STOP".equalsIgnoreCase(finishReason)
                || "STOP_BY_MODERATION".equalsIgnoreCase(finishReason);
    }

    private String conversationId(ChatRequest req) {
        return (req.conversationId() == null || req.conversationId().isBlank())
                ? "default" : req.conversationId();
    }

    public record ChatRequest(String message, String conversationId) {}
}
