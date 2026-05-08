package com.kosta.agent.web;

import com.kosta.agent.domain.Customer;
import com.kosta.agent.domain.CustomerRepository;
import com.kosta.agent.domain.Order;
import com.kosta.agent.domain.OrderRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * 주문/고객 도메인 REST 엔드포인트 (AI 미사용).
 * 단계별 학습에서 도메인 데이터가 정상 적재되었는지 확인하는 용도.
 */
@RestController
@RequestMapping("/api")
public class OrderController {

    private final CustomerRepository customers;
    private final OrderRepository orders;

    public OrderController(CustomerRepository customers, OrderRepository orders) {
        this.customers = customers;
        this.orders = orders;
    }

    @GetMapping("/customers")
    public List<Customer> listCustomers() {
        return customers.findAll();
    }

    @GetMapping("/customers/{id}")
    public Optional<Customer> getCustomer(@PathVariable Long id) {
        return customers.findById(id);
    }

    @GetMapping("/orders")
    public List<Order> listOrders() {
        return orders.findAll();
    }

    @GetMapping("/orders/{id}")
    public Optional<Order> getOrder(@PathVariable Long id) {
        return orders.findById(id);
    }

    @GetMapping("/customers/{customerId}/orders")
    public List<Order> getOrdersByCustomer(@PathVariable Long customerId) {
        return orders.findTop10ByCustomerIdOrderByCreatedAtDesc(customerId);
    }
}
