package com.kosta.agent.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 반품/환불 요청 기록. requestRefund 도구가 접수 시 생성한다. */
@Entity
@Table(name = "refund_requests")
public class RefundRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false, length = 500)
    private String reason;

    // REQUESTED, APPROVED, REJECTED
    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected RefundRequest() {}

    public RefundRequest(Long orderId, String reason) {
        this.orderId = orderId;
        this.reason = reason;
        this.status = "REQUESTED";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public String getReason() { return reason; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
