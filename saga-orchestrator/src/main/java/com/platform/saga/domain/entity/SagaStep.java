package com.platform.saga.domain.entity;

public enum SagaStep {
    RESERVE_INVENTORY,
    PROCESS_PAYMENT,
    SEND_NOTIFICATION,
    COMPLETE_ORDER,
    COMPENSATE_PAYMENT,
    RELEASE_INVENTORY,
    CANCEL_ORDER
}
