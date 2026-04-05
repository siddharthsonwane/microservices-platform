package com.platform.order.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// ─── Request DTOs ─────────────────────────────────────────────────────────────

@Data @NoArgsConstructor @AllArgsConstructor @Builder
class CreateOrderRequest {

    @NotBlank(message = "Customer ID is required")
    private String customerId;

    @NotEmpty(message = "Order must have at least one item")
    @Valid
    private List<OrderItemRequest> items;

    @NotBlank(message = "Shipping address is required")
    @Size(max = 500)
    private String shippingAddress;
}

@Data @NoArgsConstructor @AllArgsConstructor @Builder
class OrderItemRequest {

    @NotBlank(message = "Product ID is required")
    private String productId;

    @NotBlank(message = "Product name is required")
    private String productName;

    @NotNull @Min(1) @Max(100)
    private Integer quantity;

    @NotNull @DecimalMin("0.01")
    private BigDecimal unitPrice;
}

// ─── Response DTOs ────────────────────────────────────────────────────────────

@Data @NoArgsConstructor @AllArgsConstructor @Builder
class OrderResponse {
    private UUID id;
    private String customerId;
    private String sagaId;
    private String status;
    private BigDecimal totalAmount;
    private String shippingAddress;
    private String failureReason;
    private List<OrderItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

@Data @NoArgsConstructor @AllArgsConstructor @Builder
class OrderItemResponse {
    private UUID id;
    private String productId;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
}

@Data @NoArgsConstructor @AllArgsConstructor @Builder
class OrderSummaryResponse {
    private UUID id;
    private String customerId;
    private String status;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;
}
