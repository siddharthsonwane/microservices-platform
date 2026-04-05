package com.platform.order.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_orders_customer_id",  columnList = "customer_id"),
    @Index(name = "idx_orders_status",       columnList = "status"),
    @Index(name = "idx_orders_saga_id",      columnList = "saga_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "saga_id")
    private String sagaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "shipping_address", nullable = false)
    private String shippingAddress;

    @Column(name = "failure_reason")
    private String failureReason;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Domain methods
    public void confirm()  { this.status = OrderStatus.CONFIRMED; }
    public void complete() { this.status = OrderStatus.COMPLETED; }
    public void cancel(String reason) {
        this.status = OrderStatus.CANCELLED;
        this.failureReason = reason;
    }
    public void markPaymentPending() { this.status = OrderStatus.PAYMENT_PENDING; }
    public void markInventoryPending() { this.status = OrderStatus.INVENTORY_PENDING; }

    public void addItem(OrderItem item) {
        item.setOrder(this);
        this.items.add(item);
    }
}
