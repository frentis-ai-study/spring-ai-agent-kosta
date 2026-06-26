package com.kosta.agent.tool;

import com.kosta.agent.domain.Customer;
import com.kosta.agent.domain.CustomerRepository;
import com.kosta.agent.domain.Order;
import com.kosta.agent.domain.OrderRepository;
import com.kosta.agent.domain.RefundRequest;
import com.kosta.agent.domain.RefundRequestRepository;
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
    private final RefundRequestRepository refunds;

    public OrderTools(CustomerRepository customers, OrderRepository orders,
                      RefundRequestRepository refunds) {
        this.customers = customers;
        this.orders = orders;
        this.refunds = refunds;
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

    @Tool(description = "주문 ID로 배송 진행 상태와 예상 도착 시점을 안내한다.")
    public String trackShipment(
            @ToolParam(description = "조회할 주문 ID") Long orderId) {
        Order order = orders.findById(orderId).orElse(null);
        if (order == null) return "ORDER_NOT_FOUND";
        return switch (order.getStatus()) {
            case "PENDING", "PAID" -> "아직 출고 전입니다. 출고가 시작되면 배송 추적이 가능합니다.";
            case "SHIPPED" -> "배송 중입니다. 예상 도착은 영업일 기준 1~2일 이내입니다.";
            case "DELIVERED" -> "배송이 완료되었습니다.";
            case "CANCELLED" -> "취소된 주문이라 배송 정보가 없습니다.";
            default -> "상태를 확인할 수 없습니다: " + order.getStatus();
        };
    }

    /**
     * 반품/환불 접수. 주문 상태에 따라 정책이 다르므로 다단계로 판단한다.
     * 배송 완료 건만 반품 접수 대상이며, 미배송 건은 취소를 안내한다.
     */
    @Transactional
    @Tool(description = "배송 완료된 주문의 반품(환불)을 접수한다. 미배송 건은 취소가 적합하다고 안내하고, 상태별로 다르게 처리한다.")
    public String requestRefund(
            @ToolParam(description = "반품할 주문 ID") Long orderId,
            @ToolParam(description = "반품 사유 (예: 단순 변심, 불량)") String reason) {
        Order order = orders.findById(orderId).orElse(null);
        if (order == null) return "ORDER_NOT_FOUND";

        return switch (order.getStatus()) {
            case "CANCELLED" -> "이미 취소된 주문입니다. 결제분은 자동 환불됩니다.";
            case "PENDING", "PAID" -> "아직 배송 전이라 반품 대신 주문 취소가 적합합니다. cancelOrder로 취소를 진행하십시오.";
            case "SHIPPED" -> "현재 배송 중입니다. 수령 후 반품 접수가 가능하니, 도착 후 다시 요청해 주십시오.";
            case "DELIVERED" -> {
                RefundRequest saved = refunds.save(new RefundRequest(orderId, reason));
                yield "반품이 접수되었습니다. 접수번호 " + saved.getId()
                        + " (사유: " + reason + "). 정책 검토 후 영업일 기준 3일 내 처리됩니다.";
            }
            default -> "상태를 확인할 수 없어 반품을 접수하지 못했습니다: " + order.getStatus();
        };
    }
}
