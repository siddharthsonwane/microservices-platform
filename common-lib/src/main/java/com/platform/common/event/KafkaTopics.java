package com.platform.common.event;

public final class KafkaTopics {
    private KafkaTopics() {}

    // Order topics
    public static final String ORDER_CREATED       = "order.created";
    public static final String ORDER_CANCELLED     = "order.cancelled";
    public static final String ORDER_COMPLETED     = "order.completed";

    // Payment topics
    public static final String PAYMENT_PROCESS     = "payment.process";
    public static final String PAYMENT_PROCESSED   = "payment.processed";
    public static final String PAYMENT_FAILED      = "payment.failed";
    public static final String PAYMENT_REFUND      = "payment.refund";

    // Inventory topics
    public static final String INVENTORY_RESERVE   = "inventory.reserve";
    public static final String INVENTORY_RESERVED  = "inventory.reserved";
    public static final String INVENTORY_FAILED    = "inventory.failed";
    public static final String INVENTORY_RELEASE   = "inventory.release";

    // Notification topics
    public static final String NOTIFICATION_SEND   = "notification.send";

    // Dead-letter queues
    public static final String DLQ_SUFFIX          = ".dlq";
}
