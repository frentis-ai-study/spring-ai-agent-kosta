package com.kosta.agent.web;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    @PostMapping("/simple")
    public String simpleCall(@RequestBody String req) {
        return  agent.prompt().user(req).call().content();
    }
    @PostMapping("/agent")
    public Map<String, String> call(@RequestBody ChatRequest req) {
        String content = agent.prompt().user(req.message()).call().content();
        return Map.of("content", content);
    }

    @PostMapping("/agent-meta")
    public ChatResponse callWithMeta(@RequestBody ChatRequest req) {
        return agent.prompt().user(req.message()).call().chatResponse();
    }

    public record MenuResponse(
        String menuName,
        String menuDescription
    ) {}

    @PostMapping("/agent-menu")
    public MenuResponse callWithMenu(@RequestBody ChatRequest req) {
        return agent.prompt().user(req.message()).call().entity(MenuResponse.class);
    }

    @PostMapping("/agent-menus")
    public List<MenuResponse> callWithMenus(@RequestBody ChatRequest req) {
        return agent.prompt().user(req.message()).call()
            .entity(new ParameterizedTypeReference<List<MenuResponse>>() {} );
    }

    public record ChatRequest(String message) {}
}
