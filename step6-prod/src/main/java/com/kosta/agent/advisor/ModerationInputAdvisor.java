package com.kosta.agent.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

/**
 * step6: 입력 모더레이션 어드바이저.
 * 사용자 메시지에 금지어/유해 패턴이 포함되면 LLM 호출 없이 즉시 차단 응답을 생성한다.
 *
 * 운영 환경에서는 OpenAI Moderation API(OpenAiModerationModel)나
 * 사내 Safety 분류기를 호출해 동일한 위치에 반환을 단락(short-circuit)하는 패턴이다.
 */
public class ModerationInputAdvisor implements BaseAdvisor {

    private static final List<String> BLOCKED_KEYWORDS = List.of(
            "폭탄 제조", "자살 방법", "마약 구매", "신용카드번호 알려",
            "주민등록번호 유출"
    );

    private final int order;

    public ModerationInputAdvisor() {
        this(0);
    }

    public ModerationInputAdvisor(int order) {
        this.order = order;
    }

    @Override
    public String getName() {
        return "ModerationInputAdvisor";
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        Prompt prompt = request.prompt();
        String userText = prompt.getInstructions().stream()
                .filter(m -> m instanceof UserMessage)
                .map(m -> ((UserMessage) m).getText())
                .reduce("", (a, b) -> a + " " + b);

        for (String banned : BLOCKED_KEYWORDS) {
            if (userText.contains(banned)) {
                request.context().put("moderation.blocked", true);
                request.context().put("moderation.reason", banned);
                break;
            }
        }
        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        Object blocked = response.context().get("moderation.blocked");
        if (Boolean.TRUE.equals(blocked)) {
            String reason = String.valueOf(response.context().getOrDefault("moderation.reason", "policy"));
            String safe = "죄송합니다. 해당 요청은 정책상 답변드릴 수 없습니다. (사유: %s)".formatted(reason);
            // finishReason을 명시적으로 세팅: 클라이언트가 차단 사유를 식별할 수 있도록 한다
            ChatGenerationMetadata metadata = ChatGenerationMetadata.builder()
                    .finishReason("STOP_BY_MODERATION")
                    .build();
            ChatResponse safeResponse = new ChatResponse(
                    List.of(new Generation(new AssistantMessage(safe), metadata))
            );
            return ChatClientResponse.builder()
                    .chatResponse(safeResponse)
                    .context(response.context())
                    .build();
        }
        return response;
    }
}
