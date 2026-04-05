package com.platform.inventory.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_items", indexes = {
    @Index(name = "idx_inv_product_id", columnList = "product_id", unique = true)
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "product_id", nullable = false, unique = true)
    private String productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "available_quantity", nullable = false)
    private Integer availableQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    @Builder.Default
    private Integer reservedQuantity = 0;

    @Version private Long version; // optimistic locking prevents overselling

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Domain methods
    public boolean canReserve(int qty)  { return availableQuantity >= qty; }
    public void reserve(int qty)        { availableQuantity -= qty; reservedQuantity += qty; }
    public void release(int qty)        { availableQuantity += qty; reservedQuantity = Math.max(0, reservedQuantity - qty); }
    public void confirmSale(int qty)    { reservedQuantity = Math.max(0, reservedQuantity - qty); }
}
