package com.kosta.agent.mcp;

import com.kosta.agent.domain.Order;
import com.kosta.agent.domain.OrderRepository;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 우리 주문 도구를 MCP 서버로 노출한다. 외부 MCP 호스트(Claude Desktop, 사내 다른 Agent 등)가
 * `/sse` 로 접속해 호출할 수 있다.
 *
 * <p>기본 실행에는 영향이 없도록 {@code mcp} 프로파일에서만 활성화된다.
 * 실행: {@code ./gradlew bootRun --args='--spring.profiles.active=mcp'}
 *
 * <p>주의: {@code @McpTool}/{@code @McpToolParam}의 import 패키지는 MCP 어노테이션 모듈의
 * 버전에 따라 달라질 수 있다. 본 코드는 Spring AI 1.1.7 기준이다.
 */
@Component
@Profile("mcp")
public class OrderMcpTools {

    private final OrderRepository orders;

    public OrderMcpTools(OrderRepository orders) {
        this.orders = orders;
    }

    @McpTool(name = "getOrderStatus", description = "주문 ID로 주문의 현재 상태를 조회합니다.")
    public String getOrderStatus(
            @McpToolParam(description = "조회할 주문 ID", required = true) long orderId) {
        return orders.findById(orderId)
                .map(o -> "주문 " + orderId + "의 상태는 " + o.getStatus() + "입니다.")
                .orElse("주문 " + orderId + "을(를) 찾을 수 없습니다.");
    }

    @McpTool(name = "cancelOrder", description = "주문 ID로 주문을 취소합니다. 배송 완료 건은 취소할 수 없습니다.")
    @Transactional
    public String cancelOrder(
            @McpToolParam(description = "취소할 주문 ID", required = true) long orderId) {
        Order order = orders.findById(orderId).orElse(null);
        if (order == null) return "주문 " + orderId + "을(를) 찾을 수 없습니다.";
        if ("DELIVERED".equals(order.getStatus())) return "배송 완료된 주문은 취소할 수 없습니다.";
        if ("CANCELLED".equals(order.getStatus())) return "이미 취소된 주문입니다.";
        order.cancel();
        return "주문 " + orderId + "가 취소되었습니다.";
    }
}
