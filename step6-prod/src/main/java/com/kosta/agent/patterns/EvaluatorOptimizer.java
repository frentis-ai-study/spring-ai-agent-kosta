package com.kosta.agent.patterns;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluator-Optimizer 패턴 — 답변 초안을 생성하고, 평가자가 기준 충족 여부를 판정하여
 * 미달이면 피드백을 붙여 재생성한다.
 *
 * <p>핵심은 평범한 ChatClient 두 개(생성기/평가자)와 while 루프다. 무한 루프를 막기 위해
 * 최대 시도 횟수(MAX_ATTEMPTS)를 반드시 둔다.
 */
@Service
public class EvaluatorOptimizer {

    private static final int MAX_ATTEMPTS = 3;

    /** 평가자의 구조화 출력. */
    public record Evaluation(boolean pass, String feedback) {}

    /** 최종 결과(시도 횟수와 초안 이력을 함께 노출해 개선 과정을 관찰할 수 있게 한다). */
    public record RefineResult(String answer, int attempts, List<String> history) {}

    private final ChatClient generator;
    private final ChatClient evaluator;

    public EvaluatorOptimizer(ChatModel chatModel) {
        this.generator = ChatClient.builder(chatModel)
                .defaultSystem("당신은 고객 응대 문안 작성자입니다. 정중한 한국어 격식체로 간결하게 작성하십시오.")
                .build();
        this.evaluator = ChatClient.builder(chatModel)
                .defaultSystem("""
                        당신은 고객 응대 문안 검수자입니다. 아래 기준을 모두 만족하면 pass=true,
                        하나라도 미달이면 pass=false와 구체적 feedback을 제시하십시오.
                        기준: (1) 한국어 격식체 (2) 공감/사과 포함 (3) 다음 행동 안내 (4) 200자 이내
                        """)
                .build();
    }

    public RefineResult refine(String request) {
        List<String> history = new ArrayList<>();
        String draft = generator.prompt().user(request).call().content();
        history.add(draft);

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Evaluation eval = evaluator.prompt()
                    .user("다음 문안을 평가하십시오:\n" + draft)
                    .call()
                    .entity(Evaluation.class);

            if (eval.pass()) {
                return new RefineResult(draft, attempt, history);
            }

            draft = generator.prompt()
                    .user("원 요청: " + request
                            + "\n검수 피드백: " + eval.feedback()
                            + "\n피드백을 반영해 문안을 다시 작성하십시오.")
                    .call()
                    .content();
            history.add(draft);
        }
        // 최대 시도까지 기준 미달이면 마지막 초안을 반환한다.
        return new RefineResult(draft, MAX_ATTEMPTS, history);
    }
}
