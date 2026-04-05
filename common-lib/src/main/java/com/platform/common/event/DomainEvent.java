package com.platform.common.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = OrderCreatedEvent.class,      name = "ORDER_CREATED"),
    @JsonSubTypes.Type(value = OrderCancelledEvent.class,    name = "ORDER_CANCELLED"),
    @JsonSubTypes.Type(value = OrderCompletedEvent.class,    name = "ORDER_COMPLETED"),
    @JsonSubTypes.Type(value = PaymentProcessedEvent.class,  name = "PAYMENT_PROCESSED"),
    @JsonSubTypes.Type(value = PaymentFailedEvent.class,     name = "PAYMENT_FAILED"),
    @JsonSubTypes.Type(value = InventoryReservedEvent.class, name = "INVENTORY_RESERVED"),
    @JsonSubTypes.Type(value = InventoryFailedEvent.class,   name = "INVENTORY_FAILED"),
})
public abstract class DomainEvent {
    private String eventId = UUID.randomUUID().toString();
    private String sagaId;
    private String correlationId;
    private LocalDateTime occurredAt = LocalDateTime.now();
    private String eventType;
    private int version = 1;
}
