package com.kosta.agent.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosta.agent.domain.CustomerRepository;
import com.kosta.agent.domain.OrderRepository;
import com.kosta.agent.rag.PolicyIndexer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
 * step2: AgentController는 conversationId 헤더로 ChatMemory를 분리한다.
 * ChatClient는 mock으로 교체하고, 응답 라우팅과 conversationId 처리를 검증.
 */
@WebMvcTest(AgentController.class)
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

    @Test
    void conversationId가_없으면_default를_반환한다() throws Exception {
        when(callSpec.content()).thenReturn("안녕하세요");

        var body = Map.of("message", "안녕");
        mvc.perform(post("/api/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("default"))
                .andExpect(jsonPath("$.content").value("안녕하세요"));
    }

    @Test
    void 명시적_conversationId가_응답에_그대로_담긴다() throws Exception {
        when(callSpec.content()).thenReturn("기억합니다");

        var body = Map.of("message", "내 이름은 테스트", "conversationId", "session-1");
        mvc.perform(post("/api/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("session-1"));
    }
}
