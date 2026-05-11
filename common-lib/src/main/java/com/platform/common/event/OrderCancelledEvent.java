package com.platform.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public  class OrderCancelledEvent extends DomainEvent {
    {
        setEventType("ORDER_CANCELLED");
    }

    private String orderId;
    private String reason;
}
