package com.kosta.agent.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosta.agent.domain.CustomerRepository;
import com.kosta.agent.domain.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AgentController WebMvc 테스트.
 * ChatClient 빈을 Mockito mock으로 교체하여 LLM 호출 없이 컨트롤러 라우팅/응답 직렬화만 검증.
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

    private ChatClient.ChatClientRequestSpec spec;
    private ChatClient.CallResponseSpec callSpec;

    @BeforeEach
    void setUp() {
        spec = mock(ChatClient.ChatClientRequestSpec.class);
        callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
    }

    @Test
    void 단발_챗_호출은_ChatClient_응답을_그대로_반환한다() throws Exception {
        when(callSpec.content()).thenReturn("Spring AI는 자바용 AI 통합 프레임워크입니다.");

        var body = Map.of("message", "Spring AI가 무엇입니까?");
        mvc.perform(post("/api/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Spring AI는 자바용 AI 통합 프레임워크입니다."));
    }
}
