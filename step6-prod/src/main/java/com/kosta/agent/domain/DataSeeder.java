package com.kosta.agent.domain;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** 데모용 초기 데이터를 1회 적재한다. */
@Component
class DataSeeder implements CommandLineRunner {

    private final CustomerRepository customers;
    private final OrderRepository orders;
    private final ProductRepository products;

    DataSeeder(CustomerRepository customers, OrderRepository orders, ProductRepository products) {
        this.customers = customers;
        this.orders = orders;
        this.products = products;
    }

    @Override
    public void run(String... args) {
        if (customers.count() > 0) return;

        Customer alice = customers.save(new Customer("alice@example.com", "앨리스", "GOLD"));
        Customer bob = customers.save(new Customer("bob@example.com", "밥", "BASIC"));

        orders.save(new Order(alice.getId(), "기계식 키보드", 1, new BigDecimal("129000"), "DELIVERED"));
        orders.save(new Order(alice.getId(), "27인치 모니터", 1, new BigDecimal("349000"), "SHIPPED"));
        orders.save(new Order(alice.getId(), "USB-C 허브", 2, new BigDecimal("58000"), "PAID"));
        orders.save(new Order(bob.getId(), "노이즈캔슬링 헤드폰", 1, new BigDecimal("289000"), "PENDING"));

        // 재고 시연용. USB-C 허브는 품절(0) 상태로 두어 "재고 없음" 흐름을 보여준다.
        products.save(new Product("기계식 키보드", new BigDecimal("129000"), 12));
        products.save(new Product("27인치 모니터", new BigDecimal("349000"), 3));
        products.save(new Product("USB-C 허브", new BigDecimal("58000"), 0));
        products.save(new Product("노이즈캔슬링 헤드폰", new BigDecimal("289000"), 7));
        products.save(new Product("무선 마우스", new BigDecimal("39000"), 25));
    }
}
