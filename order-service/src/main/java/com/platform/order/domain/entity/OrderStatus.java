package com.platform.order.domain.entity;

public enum OrderStatus {
    PENDING,
    INVENTORY_PENDING,
    PAYMENT_PENDING,
    CONFIRMED,
    COMPLETED,
    CANCELLED,
    REFUNDED
}
