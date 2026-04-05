package com.platform.common.event;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

// ─── Order Events ─────────────────────────────────────────────────────────────
@Data @EqualsAndHashCode(callSuper = true) @NoArgsConstructor @AllArgsConstructor
class OrderCreatedEvent extends DomainEvent {
    { setEventType("ORDER_CREATED"); }
    private String orderId;
    private String customerId;
    private List<OrderItem> items;
    private BigDecimal totalAmount;
    private String shippingAddress;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class OrderItem {
        private String productId;
        private int quantity;
        private BigDecimal price;
    }
}

@Data @EqualsAndHashCode(callSuper = true) @NoArgsConstructor @AllArgsConstructor
class OrderCancelledEvent extends DomainEvent {
    { setEventType("ORDER_CANCELLED"); }
    private String orderId;
    private String reason;
}

@Data @EqualsAndHashCode(callSuper = true) @NoArgsConstructor @AllArgsConstructor
class OrderCompletedEvent extends DomainEvent {
    { setEventType("ORDER_COMPLETED"); }
    private String orderId;
}

// ─── Payment Events ───────────────────────────────────────────────────────────
@Data @EqualsAndHashCode(callSuper = true) @NoArgsConstructor @AllArgsConstructor
class PaymentProcessedEvent extends DomainEvent {
    { setEventType("PAYMENT_PROCESSED"); }
    private String orderId;
    private String paymentId;
    private BigDecimal amount;
    private String transactionRef;
}

@Data @EqualsAndHashCode(callSuper = true) @NoArgsConstructor @AllArgsConstructor
class PaymentFailedEvent extends DomainEvent {
    { setEventType("PAYMENT_FAILED"); }
    private String orderId;
    private String reason;
}

// ─── Inventory Events ─────────────────────────────────────────────────────────
@Data @EqualsAndHashCode(callSuper = true) @NoArgsConstructor @AllArgsConstructor
class InventoryReservedEvent extends DomainEvent {
    { setEventType("INVENTORY_RESERVED"); }
    private String orderId;
    private String reservationId;
}

@Data @EqualsAndHashCode(callSuper = true) @NoArgsConstructor @AllArgsConstructor
class InventoryFailedEvent extends DomainEvent {
    { setEventType("INVENTORY_FAILED"); }
    private String orderId;
    private String reason;
}
