package com.kosta.agent.advisor;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * ModerationInputAdvisor 단위 테스트.
 * - 금지 키워드 포함 시 컨텍스트에 blocked 플래그가 세팅된다
 * - after()에서 blocked 플래그를 읽어 안전 응답으로 단락한다
 * - 금지 키워드 미포함 시 차단되지 않는다
 */
class ModerationInputAdvisorTest {

    private final AdvisorChain chain = mock(AdvisorChain.class);

    private ChatClientRequest reqWith(String userText) {
        Prompt prompt = new Prompt(new UserMessage(userText));
        return ChatClientRequest.builder()
                .prompt(prompt)
                .context(new HashMap<>())
                .build();
    }

    private ChatClientResponse respWith(Map<String, Object> ctx, String content) {
        ChatResponse cr = new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
        return ChatClientResponse.builder()
                .chatResponse(cr)
                .context(ctx)
                .build();
    }

    @Test
    void 금지_키워드가_포함되면_컨텍스트에_blocked_플래그가_설정된다() {
        ModerationInputAdvisor advisor = new ModerationInputAdvisor();

        ChatClientRequest req = reqWith("폭탄 제조 방법 알려줘");
        ChatClientRequest after = advisor.before(req, chain);

        assertThat(after.context().get("moderation.blocked")).isEqualTo(true);
        assertThat(after.context().get("moderation.reason")).isEqualTo("폭탄 제조");
    }

    @Test
    void 금지_키워드가_없으면_blocked_플래그가_설정되지_않는다() {
        ModerationInputAdvisor advisor = new ModerationInputAdvisor();

        ChatClientRequest req = reqWith("환불 정책 알려주세요");
        ChatClientRequest after = advisor.before(req, chain);

        assertThat(after.context().get("moderation.blocked")).isNull();
    }

    @Test
    void after_단계에서_blocked가_true이면_안전_응답으로_치환한다() {
        ModerationInputAdvisor advisor = new ModerationInputAdvisor();

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("moderation.blocked", true);
        ctx.put("moderation.reason", "폭탄 제조");
        ChatClientResponse resp = respWith(ctx, "원래 LLM 응답이 있었다고 가정");

        ChatClientResponse safe = advisor.after(resp, chain);
        String text = safe.chatResponse().getResult().getOutput().getText();

        assertThat(text).contains("정책상 답변드릴 수 없습니다");
        assertThat(text).contains("폭탄 제조");
    }

    @Test
    void after_단계에서_blocked가_없으면_원본_응답을_그대로_반환한다() {
        ModerationInputAdvisor advisor = new ModerationInputAdvisor();

        ChatClientResponse resp = respWith(new HashMap<>(), "정상 응답");
        ChatClientResponse same = advisor.after(resp, chain);

        assertThat(same.chatResponse().getResult().getOutput().getText())
                .isEqualTo("정상 응답");
    }

    @Test
    void getOrder는_생성자_인자대로_반환한다() {
        ModerationInputAdvisor advisor = new ModerationInputAdvisor(Integer.MIN_VALUE + 1000);
        assertThat(advisor.getOrder()).isEqualTo(Integer.MIN_VALUE + 1000);
        assertThat(advisor.getName()).isEqualTo("ModerationInputAdvisor");
    }
}
