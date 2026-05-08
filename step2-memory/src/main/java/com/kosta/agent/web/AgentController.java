package com.kosta.agent.web;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * step3: ChatClient + ChatMemory + Tool Calling.
 * conversationId로 대화 맥락을 유지하면서 OrderTools를 호출합니다.
 */
@RestController
@RequestMapping("/api")
public class AgentController {

    private final ChatClient agent;

    public AgentController(ChatClient agentChatClient) {
        this.agent = agentChatClient;
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

    private String conversationId(ChatRequest req) {
        return (req.conversationId() == null || req.conversationId().isBlank())
                ? "default" : req.conversationId();
    }

    public record ChatRequest(String message, String conversationId) {}
}
