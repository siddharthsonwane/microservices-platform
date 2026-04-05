package com.platform.inventory.infrastructure.kafka;

import com.platform.common.event.*;
import com.platform.inventory.application.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventConsumer {

    private final InventoryService inventoryService;

    @KafkaListener(
        topics = {
            KafkaTopics.INVENTORY_RESERVE,
            KafkaTopics.INVENTORY_RELEASE,
            KafkaTopics.ORDER_COMPLETED
        },
        groupId = "inventory-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, DomainEvent> record, Acknowledgment ack) {
        DomainEvent event = record.value();
        log.info("Inventory received: {} for orderId: {}", event.getEventType(), record.key());

        try {
            switch (event.getEventType()) {
                case "ORDER_CREATED"   -> inventoryService.reserveInventory((OrderCreatedEvent) event);
                case "ORDER_CANCELLED" -> inventoryService.releaseInventory(
                        ((OrderCancelledEvent) event).getOrderId(), event.getSagaId());
                case "ORDER_COMPLETED" -> inventoryService.confirmSale(
                        ((OrderCompletedEvent) event).getOrderId());
                default -> log.warn("Unhandled event: {}", event.getEventType());
            }
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Inventory processing error: {}", ex.getMessage(), ex);
        }
    }
}
