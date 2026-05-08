package com.kosta.agent.tool;

import com.kosta.agent.domain.Customer;
import com.kosta.agent.domain.CustomerRepository;
import com.kosta.agent.domain.Order;
import com.kosta.agent.domain.OrderRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Spring AI Tool Calling — 주문 관리 도메인 함수.
 *
 * 권한 검증 정책:
 * - 본 학습용 코드에서는 호출자 ID(callerCustomerId)를 도구 파라미터로 명시적으로 받습니다.
 * - 운영 환경에서는 SecurityContextHolder에서 인증 주체를 꺼내 검증해야 합니다.
 */
@Component
public class OrderTools {

    private final CustomerRepository customers;
    private final OrderRepository orders;

    public OrderTools(CustomerRepository customers, OrderRepository orders) {
        this.customers = customers;
        this.orders = orders;
    }

    public record CustomerView(Long id, String email, String name, String tier) {}

    public record OrderView(Long id, String productName, int quantity, String totalAmount, String status) {
        static OrderView from(Order o) {
            return new OrderView(o.getId(), o.getProductName(), o.getQuantity(),
                    o.getTotalAmount().toPlainString(), o.getStatus());
        }
    }

    @Tool(description = "이메일로 고객 정보를 조회한다. 등급(tier), 이름, 고객 ID를 반환한다.")
    public CustomerView findCustomer(
            @ToolParam(description = "고객 이메일 주소 (예: alice@example.com)") String email) {
        return customers.findByEmail(email)
                .map(c -> new CustomerView(c.getId(), c.getEmail(), c.getName(), c.getTier()))
                .orElse(null);
    }

    @Tool(description = "고객 ID로 최근 주문 10건을 조회한다. callerCustomerId와 동일해야 한다.")
    public List<OrderView> getRecentOrders(
            @ToolParam(description = "조회 대상 고객 ID") Long customerId,
            @ToolParam(description = "호출자(인증된 사용자) 고객 ID. 운영 환경은 SecurityContext에서 추출.") Long callerCustomerId) {
        if (!customerId.equals(callerCustomerId)) {
            throw new SecurityException("FORBIDDEN: 본인 주문만 조회 가능합니다.");
        }
        return orders.findTop10ByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream().map(OrderView::from).toList();
    }

    @Tool(description = "주문 ID로 단일 주문의 현재 상태를 조회한다.")
    public OrderView getOrderStatus(
            @ToolParam(description = "주문 ID") Long orderId,
            @ToolParam(description = "호출자(인증된 사용자) 고객 ID") Long callerCustomerId) {
        Order order = orders.findById(orderId).orElse(null);
        if (order == null) return null;
        if (!order.getCustomerId().equals(callerCustomerId)) {
            throw new SecurityException("FORBIDDEN: 본인 주문만 조회 가능합니다.");
        }
        return OrderView.from(order);
    }

    @Transactional
    @Tool(description = "주문을 취소한다. DELIVERED 상태이거나 본인 주문이 아니면 취소할 수 없다.")
    public String cancelOrder(
            @ToolParam(description = "취소할 주문 ID") Long orderId,
            @ToolParam(description = "호출자(인증된 사용자) 고객 ID") Long callerCustomerId) {
        Order order = orders.findById(orderId).orElse(null);
        if (order == null) return "ORDER_NOT_FOUND";
        if (!order.getCustomerId().equals(callerCustomerId)) return "FORBIDDEN";
        if ("DELIVERED".equals(order.getStatus())) return "CANNOT_CANCEL_DELIVERED";
        if ("CANCELLED".equals(order.getStatus())) return "ALREADY_CANCELLED";
        order.cancel();
        return "CANCELLED";
    }
}
