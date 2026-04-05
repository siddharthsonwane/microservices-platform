package com.platform.notification.infrastructure.kafka;

import com.platform.common.event.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationEventConsumer {

    @KafkaListener(
        topics = { KafkaTopics.NOTIFICATION_SEND, KafkaTopics.ORDER_COMPLETED, KafkaTopics.ORDER_CANCELLED },
        groupId = "notification-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, DomainEvent> record, Acknowledgment ack) {
        DomainEvent event = record.value();
        log.info("Notification received: {} for orderId: {}", event.getEventType(), record.key());

        try {
            switch (event.getEventType()) {
                case "ORDER_COMPLETED" -> sendOrderConfirmation(event);
                case "ORDER_CANCELLED" -> sendCancellationNotice(event);
                default               -> sendGenericNotification(event);
            }
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Notification error: {}", ex.getMessage(), ex);
        }
    }

    private void sendOrderConfirmation(DomainEvent event) {
        // In production: integrate with AWS SES / SendGrid / Twilio
        log.info("📧 [EMAIL] Order confirmed! orderId={} sagaId={}", 
                 getOrderId(event), event.getSagaId());
        log.info("📱 [SMS]   Your order has been placed successfully.");
    }

    private void sendCancellationNotice(DomainEvent event) {
        log.info("📧 [EMAIL] Order cancelled. orderId={} reason={}", 
                 getOrderId(event), getCancelReason(event));
    }

    private void sendGenericNotification(DomainEvent event) {
        log.info("🔔 [PUSH]  Notification for event: {}", event.getEventType());
    }

    private String getOrderId(DomainEvent e) {
        if (e instanceof OrderCompletedEvent c) return c.getOrderId();
        if (e instanceof OrderCancelledEvent c) return c.getOrderId();
        return "unknown";
    }

    private String getCancelReason(DomainEvent e) {
        return e instanceof OrderCancelledEvent c ? c.getReason() : "";
    }
}
