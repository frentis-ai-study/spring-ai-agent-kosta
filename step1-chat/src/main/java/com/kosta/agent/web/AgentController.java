package com.kosta.agent.web;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * step1: 단발 챗봇 호출. Memory 없음.
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
        String content = agent.prompt().user(req.message()).call().content();
        return Map.of("content", content);
    }

    public record ChatRequest(String message) {}
}
