package com.platform.order.infrastructure.kafka;

import com.platform.common.event.*;
import com.platform.order.application.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final OrderService orderService;

    @KafkaListener(
        topics = {
            KafkaTopics.INVENTORY_RESERVED,
            KafkaTopics.INVENTORY_FAILED,
            KafkaTopics.PAYMENT_PROCESSED,
            KafkaTopics.PAYMENT_FAILED
        },
        groupId = "${spring.application.name}-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handle(ConsumerRecord<String, DomainEvent> record, Acknowledgment ack) {
        DomainEvent event = record.value();
        log.info("Order service received event: {} | sagaId: {}",
                event.getEventType(), event.getSagaId());

        try {
            switch (event.getEventType()) {
                case "INVENTORY_RESERVED" -> {
                    InventoryReservedEvent e = (InventoryReservedEvent) event;
                    orderService.onInventoryReserved(e.getSagaId());
                }
                case "INVENTORY_FAILED" -> {
                    InventoryFailedEvent e = (InventoryFailedEvent) event;
                    orderService.onInventoryFailed(e.getSagaId(), e.getReason());
                }
                case "PAYMENT_PROCESSED" -> {
                    PaymentProcessedEvent e = (PaymentProcessedEvent) event;
                    orderService.onPaymentProcessed(e.getSagaId());
                }
                case "PAYMENT_FAILED" -> {
                    PaymentFailedEvent e = (PaymentFailedEvent) event;
                    orderService.onPaymentFailed(e.getSagaId(), e.getReason());
                }
                default -> log.warn("Unhandled event type: {}", event.getEventType());
            }
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Error processing event {} for sagaId {}: {}",
                    event.getEventType(), event.getSagaId(), ex.getMessage(), ex);
            // Do NOT acknowledge - triggers retry / DLQ
        }
    }
}
