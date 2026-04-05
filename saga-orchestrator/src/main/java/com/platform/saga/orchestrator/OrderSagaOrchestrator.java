package com.platform.saga.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.event.*;
import com.platform.saga.domain.entity.*;
import com.platform.saga.domain.repository.SagaInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Saga Orchestrator implements the Orchestration-based Saga Pattern.
 *
 * Flow:
 *  ORDER_CREATED
 *    → [Step 1] RESERVE_INVENTORY  →  success: PROCESS_PAYMENT  / failure: CANCEL_ORDER
 *    → [Step 2] PROCESS_PAYMENT    →  success: SEND_NOTIFICATION / failure: RELEASE_INVENTORY → CANCEL_ORDER
 *    → [Step 3] SEND_NOTIFICATION  →  success: COMPLETE_ORDER
 *
 * Compensation (reverse):
 *  Payment failed → COMPENSATE_PAYMENT (if needed) → RELEASE_INVENTORY → CANCEL_ORDER
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSagaOrchestrator {

    private final SagaInstanceRepository sagaRepository;
    private final KafkaTemplate<String, DomainEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    // ─── Step 1: Start Saga on ORDER_CREATED ─────────────────────────────────
    @Transactional
    public void startSaga(OrderCreatedEvent event) {
        if (sagaRepository.existsByOrderId(event.getOrderId())) {
            log.warn("Saga already exists for orderId: {} — skipping (idempotent)", event.getOrderId());
            return;
        }

        log.info("Starting saga | sagaId={} orderId={}", event.getSagaId(), event.getOrderId());

        SagaInstance saga = SagaInstance.builder()
                .sagaId(event.getSagaId())
                .orderId(event.getOrderId())
                .customerId(event.getCustomerId())
                .status(SagaStatus.STARTED)
                .currentStep(SagaStep.RESERVE_INVENTORY)
                .sagaData(toJson(event))
                .build();

        sagaRepository.save(saga);

        // Trigger Step 1 — Reserve Inventory
        executeReserveInventory(saga, event);

        meterRegistry.counter("sagas.started").increment();
    }

    // ─── Step 1 → 2: Inventory Reserved → Process Payment ───────────────────
    @Transactional
    public void onInventoryReserved(InventoryReservedEvent event) {
        SagaInstance saga = loadSaga(event.getSagaId());
        log.info("Saga {} — inventory reserved, proceeding to payment", saga.getSagaId());

        saga.moveToStep(SagaStep.PROCESS_PAYMENT);
        sagaRepository.save(saga);

        executeProcessPayment(saga, event);
    }

    // ─── Compensation: Inventory Failed → Cancel Order ───────────────────────
    @Transactional
    public void onInventoryFailed(InventoryFailedEvent event) {
        SagaInstance saga = loadSaga(event.getSagaId());
        log.warn("Saga {} — inventory failed: {}", saga.getSagaId(), event.getReason());

        saga.compensate();
        saga.moveToStep(SagaStep.CANCEL_ORDER);
        sagaRepository.save(saga);

        executeCancelOrder(saga, "Inventory reservation failed: " + event.getReason());

        saga.fail(event.getReason());
        sagaRepository.save(saga);
        meterRegistry.counter("sagas.failed", "reason", "inventory").increment();
    }

    // ─── Step 2 → 3: Payment Processed → Send Notification ──────────────────
    @Transactional
    public void onPaymentProcessed(PaymentProcessedEvent event) {
        SagaInstance saga = loadSaga(event.getSagaId());
        log.info("Saga {} — payment processed, sending notification", saga.getSagaId());

        saga.moveToStep(SagaStep.SEND_NOTIFICATION);
        sagaRepository.save(saga);

        executeSendNotification(saga);
        executeCompleteOrder(saga);

        saga.complete();
        sagaRepository.save(saga);

        meterRegistry.counter("sagas.completed").increment();
        log.info("✅ Saga {} completed successfully", saga.getSagaId());
    }

    // ─── Compensation: Payment Failed → Release Inventory → Cancel ───────────
    @Transactional
    public void onPaymentFailed(PaymentFailedEvent event) {
        SagaInstance saga = loadSaga(event.getSagaId());
        log.warn("Saga {} — payment failed: {}", saga.getSagaId(), event.getReason());

        saga.compensate();
        saga.moveToStep(SagaStep.RELEASE_INVENTORY);
        sagaRepository.save(saga);

        // Compensating transaction: release the reserved inventory
        executeReleaseInventory(saga);
        executeCancelOrder(saga, "Payment failed: " + event.getReason());

        saga.fail(event.getReason());
        sagaRepository.save(saga);
        meterRegistry.counter("sagas.failed", "reason", "payment").increment();
    }

    // ─── Scheduled: Recover stale sagas (timeout handler) ───────────────────
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void recoverStaleSagas() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);
        List<SagaInstance> staleSagas = sagaRepository.findStaleSagas(threshold);

        if (!staleSagas.isEmpty()) {
            log.warn("Found {} stale sagas to recover", staleSagas.size());
            for (SagaInstance saga : staleSagas) {
                log.warn("Compensating stale saga: {} step={}", saga.getSagaId(), saga.getCurrentStep());
                saga.fail("Timeout - saga exceeded 10 minutes without completion");
                executeCancelOrder(saga, "Saga timeout");
                sagaRepository.save(saga);
                meterRegistry.counter("sagas.timeout").increment();
            }
        }
    }

    // ─── Private: Command Dispatchers ────────────────────────────────────────

    private void executeReserveInventory(SagaInstance saga, OrderCreatedEvent orderEvent) {
        // Re-use the ORDER_CREATED event payload; inventory service listens on this topic
        send(KafkaTopics.INVENTORY_RESERVE, saga.getOrderId(), orderEvent);
        log.debug("→ Dispatched RESERVE_INVENTORY for saga {}", saga.getSagaId());
    }

    private void executeProcessPayment(SagaInstance saga, InventoryReservedEvent inventoryEvent) {
        // Deserialize original order data to get the amount
        OrderCreatedEvent original = fromJson(saga.getSagaData(), OrderCreatedEvent.class);

        PaymentProcessedEvent cmd = new PaymentProcessedEvent();
        cmd.setOrderId(saga.getOrderId());
        cmd.setSagaId(saga.getSagaId());
        cmd.setAmount(original != null ? original.getTotalAmount() : null);
        cmd.setTransactionRef(UUID.randomUUID().toString());

        send(KafkaTopics.PAYMENT_PROCESS, saga.getOrderId(), cmd);
        log.debug("→ Dispatched PROCESS_PAYMENT for saga {}", saga.getSagaId());
    }

    private void executeSendNotification(SagaInstance saga) {
        OrderCompletedEvent event = new OrderCompletedEvent();
        event.setOrderId(saga.getOrderId());
        event.setSagaId(saga.getSagaId());

        send(KafkaTopics.NOTIFICATION_SEND, saga.getOrderId(), event);
        log.debug("→ Dispatched SEND_NOTIFICATION for saga {}", saga.getSagaId());
    }

    private void executeCompleteOrder(SagaInstance saga) {
        OrderCompletedEvent event = new OrderCompletedEvent();
        event.setOrderId(saga.getOrderId());
        event.setSagaId(saga.getSagaId());

        send(KafkaTopics.ORDER_COMPLETED, saga.getOrderId(), event);
        log.debug("→ Dispatched ORDER_COMPLETED for saga {}", saga.getSagaId());
    }

    private void executeReleaseInventory(SagaInstance saga) {
        OrderCancelledEvent cmd = new OrderCancelledEvent();
        cmd.setOrderId(saga.getOrderId());
        cmd.setSagaId(saga.getSagaId());
        cmd.setReason("Payment compensation - releasing inventory");

        send(KafkaTopics.INVENTORY_RELEASE, saga.getOrderId(), cmd);
        log.debug("→ Dispatched RELEASE_INVENTORY (compensation) for saga {}", saga.getSagaId());
    }

    private void executeCancelOrder(SagaInstance saga, String reason) {
        OrderCancelledEvent event = new OrderCancelledEvent();
        event.setOrderId(saga.getOrderId());
        event.setSagaId(saga.getSagaId());
        event.setReason(reason);

        send(KafkaTopics.ORDER_CANCELLED, saga.getOrderId(), event);
        log.debug("→ Dispatched ORDER_CANCELLED for saga {}", saga.getSagaId());
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    private SagaInstance loadSaga(String sagaId) {
        return sagaRepository.findById(sagaId)
                .orElseThrow(() -> new IllegalStateException("Saga not found: " + sagaId));
    }

    private void send(String topic, String key, DomainEvent event) {
        kafkaTemplate.send(topic, key, event).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send to topic {}: {}", topic, ex.getMessage());
            }
        });
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("JSON serialization failed", e);
            return "{}";
        }
    }

    private <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.error("JSON deserialization failed", e);
            return null;
        }
    }
}
