package com.platform.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class InventoryFailedEvent extends DomainEvent {
    {
        setEventType("INVENTORY_FAILED");
    }

    private String orderId;
    private String reason;
}
