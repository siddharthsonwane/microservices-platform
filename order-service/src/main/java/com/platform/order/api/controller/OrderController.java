package com.platform.order.api.controller;

import com.platform.common.dto.ApiResponse;
import com.platform.order.application.dto.*;
import com.platform.order.application.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {
        log.info("POST /api/v1/orders - customer: {}", request.getCustomerId());
        OrderResponse order = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(order, "Order created and saga initiated"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderById(id)));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<Page<OrderSummaryResponse>>> getOrdersByCustomer(
            @PathVariable String customerId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        Page<OrderSummaryResponse> orders = orderService.getOrdersByCustomer(customerId, pageable);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "Cancelled by customer") String reason) {
        OrderResponse order = orderService.cancelOrder(id, reason);
        return ResponseEntity.ok(ApiResponse.success(order, "Order cancelled"));
    }
}
