package com.platform.payment.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payments_order_id", columnList = "order_id"),
    @Index(name = "idx_payments_saga_id",  columnList = "saga_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id",    nullable = false) private String orderId;
    @Column(name = "saga_id")                       private String sagaId;
    @Column(name = "customer_id", nullable = false) private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    @Builder.Default
    private String currency = "USD";

    @Column(name = "transaction_ref", unique = true)
    private String transactionRef;

    @Column(name = "gateway_response")
    private String gatewayResponse;

    @Column(name = "failure_reason")
    private String failureReason;

    @Version private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // Domain methods
    public void succeed(String transactionRef) {
        this.status = PaymentStatus.SUCCESS;
        this.transactionRef = transactionRef;
    }

    public void fail(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }

    public void refund() { this.status = PaymentStatus.REFUNDED; }
}
