package com.kosta.agent.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DataSeeder 동작 검증.
 * SpringBootTest 부팅 후 시드 데이터(alice, bob 등)가 적재되는지 확인.
 */
@SpringBootTest
@ActiveProfiles("test")
class DataSeederTest {

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private OrderRepository orders;

    @Test
    void DataSeeder가_초기_고객과_주문을_생성한다() {
        assertThat(customers.findByEmail("alice@example.com")).isPresent();
        assertThat(customers.findByEmail("bob@example.com")).isPresent();

        Customer alice = customers.findByEmail("alice@example.com").orElseThrow();
        var aliceOrders = orders.findTop10ByCustomerIdOrderByCreatedAtDesc(alice.getId());
        assertThat(aliceOrders).hasSizeGreaterThanOrEqualTo(3);
    }
}
