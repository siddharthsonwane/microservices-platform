package com.platform.payment.infrastructure.kafka;

import com.platform.common.event.*;
import com.platform.payment.application.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final PaymentService paymentService;

    @KafkaListener(
        topics = { KafkaTopics.PAYMENT_PROCESS, KafkaTopics.PAYMENT_REFUND },
        groupId = "payment-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, DomainEvent> record, Acknowledgment ack) {
        DomainEvent event = record.value();
        log.info("Payment service received: {} for orderId: {}", event.getEventType(), record.key());

        try {
            switch (event.getEventType()) {
                case "PAYMENT_PROCESSED" -> paymentService.processPayment((PaymentProcessedEvent) event);
                case "ORDER_CANCELLED"   -> paymentService.refundPayment(event.getSagaId());
                default -> log.warn("Unhandled event: {}", event.getEventType());
            }
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Payment processing error: {}", ex.getMessage(), ex);
        }
    }
}
