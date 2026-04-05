package com.platform.order.application.service;

import com.platform.common.event.KafkaTopics;
import com.platform.common.exception.ResourceNotFoundException;
import com.platform.order.application.dto.*;
import com.platform.order.domain.entity.Order;
import com.platform.order.domain.entity.OrderItem;
import com.platform.order.domain.entity.OrderStatus;
import com.platform.order.domain.repository.OrderRepository;
import com.platform.order.infrastructure.kafka.OrderEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository    orderRepository;
    private final OrderEventPublisher eventPublisher;
    private final MeterRegistry      meterRegistry;

    // ─── Create Order ─────────────────────────────────────────────────────────
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);

        log.info("Creating order for customer: {}", request.getCustomerId());

        // Build domain entity
        Order order = Order.builder()
                .customerId(request.getCustomerId())
                .shippingAddress(request.getShippingAddress())
                .status(OrderStatus.PENDING)
                .build();

        // Map items and calculate total
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequest itemReq : request.getItems()) {
            OrderItem item = OrderItem.builder()
                    .productId(itemReq.getProductId())
                    .productName(itemReq.getProductName())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(itemReq.getUnitPrice())
                    .build();
            order.addItem(item);
            total = total.add(itemReq.getUnitPrice()
                    .multiply(BigDecimal.valueOf(itemReq.getQuantity())));
        }
        order.setTotalAmount(total);

        Order savedOrder = orderRepository.save(order);
        log.info("Order created with id: {}", savedOrder.getId());

        // Publish ORDER_CREATED event to kick off the saga
        eventPublisher.publishOrderCreated(savedOrder);

        meterRegistry.counter("orders.created").increment();
        sample.stop(meterRegistry.timer("orders.creation.time"));

        return toResponse(savedOrder);
    }

    // ─── Get Order ────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID id) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        return toResponse(order);
    }

    // ─── List Customer Orders ─────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> getOrdersByCustomer(String customerId, Pageable pageable) {
        return orderRepository.findByCustomerId(customerId, pageable)
                .map(this::toSummaryResponse);
    }

    // ─── Saga State Machine Callbacks ─────────────────────────────────────────
    @Transactional
    public void onInventoryReserved(String sagaId) {
        Order order = findBySagaId(sagaId);
        order.markPaymentPending();
        orderRepository.save(order);
        log.info("Order {} - inventory reserved, moving to PAYMENT_PENDING", order.getId());
    }

    @Transactional
    public void onInventoryFailed(String sagaId, String reason) {
        Order order = findBySagaId(sagaId);
        order.cancel("Inventory reservation failed: " + reason);
        orderRepository.save(order);
        log.warn("Order {} - inventory failed: {}", order.getId(), reason);
        meterRegistry.counter("orders.cancelled", "reason", "inventory_failed").increment();
    }

    @Transactional
    public void onPaymentProcessed(String sagaId) {
        Order order = findBySagaId(sagaId);
        order.complete();
        orderRepository.save(order);
        log.info("Order {} - payment processed, order COMPLETED", order.getId());
        meterRegistry.counter("orders.completed").increment();
    }

    @Transactional
    public void onPaymentFailed(String sagaId, String reason) {
        Order order = findBySagaId(sagaId);
        order.cancel("Payment failed: " + reason);
        orderRepository.save(order);
        log.warn("Order {} - payment failed: {}", order.getId(), reason);
        // Trigger compensating transaction - release inventory
        eventPublisher.publishInventoryRelease(order.getId().toString(), sagaId);
        meterRegistry.counter("orders.cancelled", "reason", "payment_failed").increment();
    }

    // ─── Cancel Order (manual) ────────────────────────────────────────────────
    @Transactional
    public OrderResponse cancelOrder(UUID id, String reason) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));

        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel a completed order");
        }
        order.cancel(reason);
        orderRepository.save(order);
        eventPublisher.publishOrderCancelled(order, reason);
        log.info("Order {} manually cancelled: {}", id, reason);
        return toResponse(order);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    private Order findBySagaId(String sagaId) {
        return orderRepository.findBySagaId(sagaId)
                .orElseThrow(() -> new ResourceNotFoundException("Order with sagaId: " + sagaId));
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(i -> OrderItemResponse.builder()
                        .id(i.getId())
                        .productId(i.getProductId())
                        .productName(i.getProductName())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .totalPrice(i.getTotalPrice())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .customerId(order.getCustomerId())
                .sagaId(order.getSagaId())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .shippingAddress(order.getShippingAddress())
                .failureReason(order.getFailureReason())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private OrderSummaryResponse toSummaryResponse(Order order) {
        return OrderSummaryResponse.builder()
                .id(order.getId())
                .customerId(order.getCustomerId())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
