package com.kosta.agent.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OrderRepository 슬라이스 테스트.
 * H2 인메모리 + PostgreSQL 호환 모드로 동작.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orders;

    @Autowired
    private CustomerRepository customers;

    @Test
    void 고객별_최근_주문_10건을_생성순_역순으로_조회한다() {
        Customer alice = customers.save(new Customer("alice@test.com", "앨리스", "GOLD"));
        Customer bob = customers.save(new Customer("bob@test.com", "밥", "BASIC"));

        for (int i = 0; i < 12; i++) {
            orders.save(new Order(alice.getId(), "상품" + i, 1, new BigDecimal("1000"), "PAID"));
        }
        orders.save(new Order(bob.getId(), "밥의 상품", 1, new BigDecimal("500"), "PAID"));

        List<Order> result = orders.findTop10ByCustomerIdOrderByCreatedAtDesc(alice.getId());

        assertThat(result).hasSize(10);
        assertThat(result).allSatisfy(o -> assertThat(o.getCustomerId()).isEqualTo(alice.getId()));
    }

    @Test
    void 이메일로_고객을_조회할_수_있다() {
        customers.save(new Customer("query@test.com", "쿼리유저", "VIP"));

        var found = customers.findByEmail("query@test.com");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("쿼리유저");
        assertThat(found.get().getTier()).isEqualTo("VIP");
    }

    @Test
    void 주문을_취소하면_상태가_CANCELLED가_된다() {
        Customer c = customers.save(new Customer("cancel@test.com", "취소", "BASIC"));
        Order o = orders.save(new Order(c.getId(), "취소대상", 1, new BigDecimal("1000"), "PAID"));

        o.cancel();
        orders.save(o);

        Order reloaded = orders.findById(o.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo("CANCELLED");
    }
}
