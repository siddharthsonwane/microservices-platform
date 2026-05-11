package com.platform.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// ─── Payment Events ───────────────────────────────────────────────────────────
@Data
@EqualsAndHashCode(callSuper = true) @NoArgsConstructor
@AllArgsConstructor
public  class PaymentProcessedEvent extends DomainEvent {
    {
        setEventType("PAYMENT_PROCESSED"); }
    private String orderId;
    private String paymentId;
    private BigDecimal amount;
    private String transactionRef;
}
