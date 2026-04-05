package com.platform.order.infrastructure.kafka;

import com.platform.common.event.*;
import com.platform.order.domain.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final KafkaTemplate<String, DomainEvent> kafkaTemplate;

    public void publishOrderCreated(Order order) {
        OrderCreatedEvent event = new OrderCreatedEvent();
        event.setOrderId(order.getId().toString());
        event.setCustomerId(order.getCustomerId());
        event.setTotalAmount(order.getTotalAmount());
        event.setShippingAddress(order.getShippingAddress());
        event.setSagaId(UUID.randomUUID().toString());
        event.setItems(order.getItems().stream()
                .map(i -> new OrderCreatedEvent.OrderItem(
                        i.getProductId(), i.getQuantity(), i.getUnitPrice()))
                .collect(Collectors.toList()));

        // Store saga ID on order
        order.setSagaId(event.getSagaId());

        send(KafkaTopics.ORDER_CREATED, order.getId().toString(), event);
    }

    public void publishOrderCancelled(Order order, String reason) {
        OrderCancelledEvent event = new OrderCancelledEvent();
        event.setOrderId(order.getId().toString());
        event.setSagaId(order.getSagaId());
        event.setReason(reason);
        send(KafkaTopics.ORDER_CANCELLED, order.getId().toString(), event);
    }

    public void publishInventoryRelease(String orderId, String sagaId) {
        // Compensating transaction: release inventory after payment failure
        OrderCancelledEvent event = new OrderCancelledEvent();
        event.setOrderId(orderId);
        event.setSagaId(sagaId);
        event.setReason("Payment failed - releasing inventory");
        send(KafkaTopics.INVENTORY_RELEASE, orderId, event);
    }

    private void send(String topic, String key, DomainEvent event) {
        CompletableFuture<SendResult<String, DomainEvent>> future =
                kafkaTemplate.send(topic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send event {} to topic {}: {}",
                        event.getEventType(), topic, ex.getMessage(), ex);
            } else {
                log.info("Event {} sent to topic {} partition {} offset {}",
                        event.getEventType(), topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
