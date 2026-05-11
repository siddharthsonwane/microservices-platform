package com.platform.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class OrderCompletedEvent extends DomainEvent {
    {
        setEventType("ORDER_COMPLETED");
    }

    private String orderId;
}
