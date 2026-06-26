package com.kosta.agent.patterns;

import com.kosta.agent.tool.CatalogTools;
import com.kosta.agent.tool.OrderTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

/**
 * Routing 패턴 — 들어온 문의를 유형으로 분류한 뒤 전담 처리기로 분기한다.
 *
 * <p>분류기와 각 처리기 모두 평범한 ChatClient 호출이다. "패턴"은 마법 클래스가 아니라
 * 분류용 호출 1회 + switch 분기일 뿐이며, 별도 인프라가 필요 없다.
 */
@Service
public class RoutingWorkflow {

    public enum InquiryType { ORDER, POLICY, CHITCHAT }

    /** 분류기의 구조화 출력. */
    public record Routing(InquiryType type, String reasoning) {}

    /** 최종 결과(분류 결과를 함께 노출해 흐름을 관찰할 수 있게 한다). */
    public record RoutingResult(InquiryType type, String reasoning, String answer) {}

    private final ChatClient classifier;
    private final ChatClient orderAgent;
    private final ChatClient policyAgent;
    private final ChatClient chitchatAgent;

    public RoutingWorkflow(ChatModel chatModel, OrderTools orderTools, CatalogTools catalogTools) {
        this.classifier = ChatClient.builder(chatModel)
                .defaultSystem("""
                        고객 문의를 다음 한 가지 유형으로 분류하십시오.
                        - ORDER: 주문 조회/취소/배송/반품/재고 등 주문 관련
                        - POLICY: 환불 규정, 배송비, 멤버십 혜택 등 정책/FAQ
                        - CHITCHAT: 인사, 잡담 등 그 외
                        분류 근거(reasoning)도 한 문장으로 제시하십시오.
                        """)
                .build();
        this.orderAgent = ChatClient.builder(chatModel)
                .defaultSystem("당신은 주문 처리 상담사입니다. 제공된 도구로 정확히 처리하고 한국어 격식체로 답하십시오.")
                .defaultTools(orderTools, catalogTools)
                .build();
        this.policyAgent = ChatClient.builder(chatModel)
                .defaultSystem("당신은 정책 안내 상담사입니다. 환불/배송/멤버십 정책을 한국어 격식체로 명확히 안내하십시오.")
                .build();
        this.chitchatAgent = ChatClient.builder(chatModel)
                .defaultSystem("당신은 친절한 쇼핑몰 상담사입니다. 짧고 정중하게 응대하고, 필요하면 주문/정책 문의를 권하십시오.")
                .build();
    }

    public RoutingResult handle(String message) {
        Routing routing = classifier.prompt().user(message).call().entity(Routing.class);

        ChatClient target = switch (routing.type()) {
            case ORDER -> orderAgent;
            case POLICY -> policyAgent;
            case CHITCHAT -> chitchatAgent;
        };
        String answer = target.prompt().user(message).call().content();
        return new RoutingResult(routing.type(), routing.reasoning(), answer);
    }
}
