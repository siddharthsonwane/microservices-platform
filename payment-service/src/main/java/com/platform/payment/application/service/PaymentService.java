package com.platform.payment.application.service;

import com.platform.common.event.*;
import com.platform.payment.domain.entity.*;
import com.platform.payment.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository            paymentRepository;
    private final KafkaTemplate<String, DomainEvent> kafkaTemplate;
    private final MeterRegistry                meterRegistry;

    @Transactional
    public void processPayment(PaymentProcessedEvent cmd) {
        // Idempotency guard
        if (paymentRepository.existsByOrderId(cmd.getOrderId())) {
            log.warn("Payment already processed for orderId: {} — skipping", cmd.getOrderId());
            return;
        }

        log.info("Processing payment | orderId={} sagaId={} amount={}",
                cmd.getOrderId(), cmd.getSagaId(), cmd.getAmount());

        Payment payment = Payment.builder()
                .orderId(cmd.getOrderId())
                .sagaId(cmd.getSagaId())
                .customerId("unknown") // would come from order context in real scenario
                .amount(cmd.getAmount())
                .build();

        try {
            // ── Simulate payment gateway call ──────────────────────────────
            String transactionRef = simulateGatewayCharge(payment);
            payment.succeed(transactionRef);
            paymentRepository.save(payment);

            // Publish success event
            PaymentProcessedEvent successEvent = new PaymentProcessedEvent();
            successEvent.setOrderId(cmd.getOrderId());
            successEvent.setSagaId(cmd.getSagaId());
            successEvent.setPaymentId(payment.getId().toString());
            successEvent.setAmount(cmd.getAmount());
            successEvent.setTransactionRef(transactionRef);

            kafkaTemplate.send(KafkaTopics.PAYMENT_PROCESSED, cmd.getOrderId(), successEvent);
            meterRegistry.counter("payments.success").increment();
            log.info("✅ Payment succeeded | orderId={} txRef={}", cmd.getOrderId(), transactionRef);

        } catch (PaymentGatewayException ex) {
            payment.fail(ex.getMessage());
            paymentRepository.save(payment);

            PaymentFailedEvent failEvent = new PaymentFailedEvent();
            failEvent.setOrderId(cmd.getOrderId());
            failEvent.setSagaId(cmd.getSagaId());
            failEvent.setReason(ex.getMessage());

            kafkaTemplate.send(KafkaTopics.PAYMENT_FAILED, cmd.getOrderId(), failEvent);
            meterRegistry.counter("payments.failed").increment();
            log.error("❌ Payment failed | orderId={} reason={}", cmd.getOrderId(), ex.getMessage());
        }
    }

    @Transactional
    public void refundPayment(String sagaId) {
        paymentRepository.findBySagaId(sagaId).ifPresent(payment -> {
            payment.refund();
            paymentRepository.save(payment);
            log.info("Payment refunded for sagaId: {}", sagaId);
            meterRegistry.counter("payments.refunded").increment();
        });
    }

    // Simulates external gateway — in production replace with Stripe/Braintree SDK
    private String simulateGatewayCharge(Payment payment) {
        // Simulate 5% failure rate for demonstration
        if (Math.random() < 0.05) {
            throw new PaymentGatewayException("Insufficient funds");
        }
        return "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    static class PaymentGatewayException extends RuntimeException {
        PaymentGatewayException(String msg) { super(msg); }
    }
}
