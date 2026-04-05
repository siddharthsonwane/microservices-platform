package com.platform.saga.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "saga_instances", indexes = {
    @Index(name = "idx_saga_order_id",  columnList = "order_id"),
    @Index(name = "idx_saga_status",    columnList = "status"),
    @Index(name = "idx_saga_step",      columnList = "current_step")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SagaInstance {

    @Id
    @Column(name = "saga_id")
    private String sagaId;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private SagaStatus status = SagaStatus.STARTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", nullable = false)
    @Builder.Default
    private SagaStep currentStep = SagaStep.RESERVE_INVENTORY;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "saga_data", columnDefinition = "jsonb")
    private String sagaData;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "retry_count")
    @Builder.Default
    private int retryCount = 0;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // Domain helpers
    public void moveToStep(SagaStep step) {
        this.currentStep = step;
        this.status = SagaStatus.IN_PROGRESS;
    }

    public void complete() {
        this.status = SagaStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void fail(String reason) {
        this.status = SagaStatus.FAILED;
        this.failureReason = reason;
        this.completedAt = LocalDateTime.now();
    }

    public void compensate() {
        this.status = SagaStatus.COMPENSATING;
    }

    public void compensated() {
        this.status = SagaStatus.COMPENSATED;
        this.completedAt = LocalDateTime.now();
    }
}
