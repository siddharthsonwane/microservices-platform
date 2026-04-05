package com.platform.inventory.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_reservations", indexes = {
    @Index(name = "idx_res_order_id", columnList = "order_id"),
    @Index(name = "idx_res_saga_id",  columnList = "saga_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InventoryReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id",   nullable = false) private String orderId;
    @Column(name = "saga_id")                      private String sagaId;
    @Column(name = "product_id", nullable = false) private String productId;
    @Column(name = "quantity",   nullable = false) private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReservationStatus status = ReservationStatus.RESERVED;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    public void release() {
        this.status = ReservationStatus.RELEASED;
        this.releasedAt = LocalDateTime.now();
    }

    public void confirm() {
        this.status = ReservationStatus.CONFIRMED;
    }
}
