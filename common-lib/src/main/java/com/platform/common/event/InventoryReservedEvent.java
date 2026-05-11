package com.platform.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

// ─── Inventory Events ─────────────────────────────────────────────────────────
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReservedEvent extends DomainEvent {
    {
        setEventType("INVENTORY_RESERVED");
    }

    private String orderId;
    private String reservationId;
}
