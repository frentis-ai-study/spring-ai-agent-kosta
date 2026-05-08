package com.kosta.agent.tool;

import com.kosta.agent.domain.Customer;
import com.kosta.agent.domain.CustomerRepository;
import com.kosta.agent.domain.Order;
import com.kosta.agent.domain.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * OrderTools 단위 테스트.
 * - 권한 분리 (callerCustomerId != customerId 시 SecurityException)
 * - cancelOrder 멱등성 (두 번 호출 시 두 번째는 ALREADY_CANCELLED)
 * Repository는 Mockito mock으로 격리.
 */
class OrderToolsTest {

    private CustomerRepository customers;
    private OrderRepository orders;
    private OrderTools tools;

    @BeforeEach
    void setUp() {
        customers = mock(CustomerRepository.class);
        orders = mock(OrderRepository.class);
        tools = new OrderTools(customers, orders);
    }

    private Order order(Long id, Long customerId, String status) {
        Order o = new Order(customerId, "테스트상품", 1, new BigDecimal("1000"), status);
        try {
            Field f = Order.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(o, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return o;
    }

    @Test
    void findCustomer는_이메일로_고객뷰를_반환한다() {
        when(customers.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(new Customer("alice@example.com", "앨리스", "GOLD")));

        OrderTools.CustomerView v = tools.findCustomer("alice@example.com");

        assertThat(v).isNotNull();
        assertThat(v.email()).isEqualTo("alice@example.com");
        assertThat(v.tier()).isEqualTo("GOLD");
    }

    @Test
    void getRecentOrders는_타인_조회시_SecurityException을_던진다() {
        assertThatThrownBy(() -> tools.getRecentOrders(1L, 2L))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("FORBIDDEN");
    }

    @Test
    void getRecentOrders는_본인_주문만_반환한다() {
        when(orders.findTop10ByCustomerIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(order(10L, 1L, "PAID")));

        List<OrderTools.OrderView> views = tools.getRecentOrders(1L, 1L);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).status()).isEqualTo("PAID");
    }

    @Test
    void getOrderStatus는_타인_주문이면_SecurityException을_던진다() {
        when(orders.findById(99L)).thenReturn(Optional.of(order(99L, 1L, "PAID")));

        assertThatThrownBy(() -> tools.getOrderStatus(99L, 2L))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void cancelOrder_DELIVERED는_취소_불가() {
        when(orders.findById(any())).thenReturn(Optional.of(order(1L, 1L, "DELIVERED")));
        String res = tools.cancelOrder(1L, 1L);
        assertThat(res).isEqualTo("CANNOT_CANCEL_DELIVERED");
    }

    @Test
    void cancelOrder는_없는_주문이면_ORDER_NOT_FOUND를_반환한다() {
        when(orders.findById(any())).thenReturn(Optional.empty());
        String res = tools.cancelOrder(999L, 1L);
        assertThat(res).isEqualTo("ORDER_NOT_FOUND");
    }

    @Test
    void cancelOrder는_타인_주문이면_FORBIDDEN을_반환한다() {
        when(orders.findById(any())).thenReturn(Optional.of(order(1L, 1L, "PAID")));
        String res = tools.cancelOrder(1L, 2L);
        assertThat(res).isEqualTo("FORBIDDEN");
    }

    @Test
    void cancelOrder는_멱등하다_두_번_호출하면_두_번째는_ALREADY_CANCELLED() {
        Order o = order(1L, 1L, "PAID");
        when(orders.findById(any())).thenReturn(Optional.of(o));

        String first = tools.cancelOrder(1L, 1L);
        // 첫 호출 후 상태가 변경됨
        String second = tools.cancelOrder(1L, 1L);

        assertThat(first).isEqualTo("CANCELLED");
        assertThat(second).isEqualTo("ALREADY_CANCELLED");
    }
}
