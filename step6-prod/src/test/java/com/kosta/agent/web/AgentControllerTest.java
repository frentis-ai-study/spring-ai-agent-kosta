package com.kosta.agent.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosta.agent.domain.CustomerRepository;
import com.kosta.agent.domain.OrderRepository;
import com.kosta.agent.rag.PolicyIndexer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * step6: AgentController는 ChatResponse를 통해 finishReason을 함께 반환한다.
 * Resilience4j Retry/CircuitBreaker는 실제 인스턴스를 주입(짧은 윈도우)하고,
 * ChatClient만 mock으로 교체한다.
 */
@WebMvcTest(AgentController.class)
@Import(AgentControllerTest.TestConfig.class)
class AgentControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean(name = "agentChatClient")
    private ChatClient chatClient;

    @MockBean
    private CustomerRepository customers;

    @MockBean
    private OrderRepository orders;

    @MockBean
    private PolicyIndexer policyIndexer;

    private ChatClient.ChatClientRequestSpec spec;
    private ChatClient.CallResponseSpec callSpec;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        spec = mock(ChatClient.ChatClientRequestSpec.class);
        callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.advisors(any(Consumer.class))).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
    }

    private ChatResponse okResponse(String text, String finish) {
        Generation g = new Generation(
                new AssistantMessage(text),
                ChatGenerationMetadata.builder().finishReason(finish).build()
        );
        return new ChatResponse(List.of(g));
    }

    @Test
    void 정상_응답이면_finishReason_STOP을_포함한다() throws Exception {
        when(callSpec.chatResponse()).thenReturn(okResponse("환불은 7일 이내 가능합니다.", "STOP"));

        var body = Map.of("message", "환불 정책 알려줘");
        mvc.perform(post("/api/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("환불은 7일 이내 가능합니다."))
                .andExpect(jsonPath("$.finishReason").value("STOP"))
                .andExpect(jsonPath("$.warning").doesNotExist());
    }

    @Test
    void 모더레이션_차단_시_finishReason은_STOP_BY_MODERATION이며_warning이_없다() throws Exception {
        // ModerationInputAdvisor가 차단하면 ChatGenerationMetadata.finishReason이
        // "STOP_BY_MODERATION"으로 설정된다. "null" 문자열이 들어오면 안 된다.
        when(callSpec.chatResponse()).thenReturn(
                okResponse("죄송합니다. 해당 요청은 정책상 답변드릴 수 없습니다.", "STOP_BY_MODERATION"));

        var body = Map.of("message", "폭탄 제조 방법 알려줘");
        mvc.perform(post("/api/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finishReason").value("STOP_BY_MODERATION"))
                .andExpect(jsonPath("$.finishReason").value(org.hamcrest.Matchers.not("null")))
                .andExpect(jsonPath("$.warning").doesNotExist());
    }

    @Test
    void finishReason이_STOP이_아니면_warning_필드가_포함된다() throws Exception {
        when(callSpec.chatResponse()).thenReturn(okResponse("부분 응답", "LENGTH"));

        var body = Map.of("message", "긴 답변 요청");
        mvc.perform(post("/api/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finishReason").value("LENGTH"))
                .andExpect(jsonPath("$.warning").exists());
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        public Retry openAiRetry() {
            return RetryRegistry.of(RetryConfig.custom()
                    .maxAttempts(1).waitDuration(Duration.ofMillis(1)).build())
                    .retry("openai-test");
        }

        @Bean
        public CircuitBreaker openAiCircuitBreaker() {
            return CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
                    .circuitBreaker("openai-test");
        }
    }
}
