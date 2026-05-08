package com.kosta.agent.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findTop10ByCustomerIdOrderByCreatedAtDesc(Long customerId);
}
