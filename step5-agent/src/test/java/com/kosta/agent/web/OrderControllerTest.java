package com.kosta.agent.web;

import com.kosta.agent.domain.Customer;
import com.kosta.agent.domain.CustomerRepository;
import com.kosta.agent.domain.Order;
import com.kosta.agent.domain.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OrderController WebMvc 슬라이스 테스트.
 * Repository는 mock으로 처리.
 */
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private CustomerRepository customers;

    @MockBean
    private OrderRepository orders;

    @Test
    void 고객_목록_조회_API가_JSON을_반환한다() throws Exception {
        Customer c = new Customer("alice@example.com", "앨리스", "GOLD");
        when(customers.findAll()).thenReturn(List.of(c));

        mvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("alice@example.com"))
                .andExpect(jsonPath("$[0].tier").value("GOLD"));
    }

    @Test
    void 고객_단일_조회는_Optional_JSON을_반환한다() throws Exception {
        Customer c = new Customer("bob@example.com", "밥", "BASIC");
        when(customers.findById(any())).thenReturn(Optional.of(c));

        mvc.perform(get("/api/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("밥"));
    }

    @Test
    void 고객별_주문_조회는_Top10_레포지토리_메서드를_사용한다() throws Exception {
        Order order = new Order(1L, "키보드", 1, new BigDecimal("129000"), "PAID");
        when(orders.findTop10ByCustomerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(order));

        mvc.perform(get("/api/customers/1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productName").value("키보드"))
                .andExpect(jsonPath("$[0].status").value("PAID"));
    }
}
