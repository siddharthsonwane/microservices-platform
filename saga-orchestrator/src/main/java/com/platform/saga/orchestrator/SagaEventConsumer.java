package com.platform.saga.orchestrator;

import com.platform.common.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaEventConsumer {

    private final OrderSagaOrchestrator orchestrator;

    @KafkaListener(
        topics = {
            KafkaTopics.ORDER_CREATED,
            KafkaTopics.INVENTORY_RESERVED,
            KafkaTopics.INVENTORY_FAILED,
            KafkaTopics.PAYMENT_PROCESSED,
            KafkaTopics.PAYMENT_FAILED
        },
        groupId = "saga-orchestrator-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, DomainEvent> record, Acknowledgment ack) {
        DomainEvent event = record.value();
        log.info("SAGA received: type={} sagaId={} orderId={}",
                event.getEventType(), event.getSagaId(), record.key());

        try {
            switch (event.getEventType()) {
                case "ORDER_CREATED"       -> orchestrator.startSaga((OrderCreatedEvent) event);
                case "INVENTORY_RESERVED"  -> orchestrator.onInventoryReserved((InventoryReservedEvent) event);
                case "INVENTORY_FAILED"    -> orchestrator.onInventoryFailed((InventoryFailedEvent) event);
                case "PAYMENT_PROCESSED"   -> orchestrator.onPaymentProcessed((PaymentProcessedEvent) event);
                case "PAYMENT_FAILED"      -> orchestrator.onPaymentFailed((PaymentFailedEvent) event);
                default -> log.warn("Saga orchestrator ignoring unknown event: {}", event.getEventType());
            }
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Saga processing failed for event {}: {}", event.getEventType(), ex.getMessage(), ex);
            // No ack → triggers DLQ via error handler
        }
    }
}
