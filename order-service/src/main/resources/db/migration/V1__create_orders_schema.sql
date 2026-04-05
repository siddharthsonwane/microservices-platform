-- V1__create_orders_schema.sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE orders (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id      VARCHAR(255)   NOT NULL,
    saga_id          VARCHAR(255)   UNIQUE,
    status           VARCHAR(50)    NOT NULL DEFAULT 'PENDING',
    total_amount     NUMERIC(19, 4) NOT NULL,
    shipping_address TEXT           NOT NULL,
    failure_reason   TEXT,
    version          BIGINT         NOT NULL DEFAULT 0,
    created_at       TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE TABLE order_items (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id     UUID           NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id   VARCHAR(255)   NOT NULL,
    product_name VARCHAR(500)   NOT NULL,
    quantity     INTEGER        NOT NULL CHECK (quantity > 0),
    unit_price   NUMERIC(19, 4) NOT NULL,
    total_price  NUMERIC(19, 4) NOT NULL
);

CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_status      ON orders(status);
CREATE INDEX idx_orders_saga_id     ON orders(saga_id);
CREATE INDEX idx_orders_created_at  ON orders(created_at DESC);
CREATE INDEX idx_order_items_order  ON order_items(order_id);
CREATE INDEX idx_order_items_product ON order_items(product_id);

-- Outbox table for reliable messaging
CREATE TABLE outbox_events (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    aggregate_id VARCHAR(255) NOT NULL,
    event_type   VARCHAR(255) NOT NULL,
    payload      JSONB        NOT NULL,
    topic        VARCHAR(255) NOT NULL,
    status       VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    retry_count  INTEGER      NOT NULL DEFAULT 0,
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    processed_at TIMESTAMP
);

CREATE INDEX idx_outbox_status     ON outbox_events(status);
CREATE INDEX idx_outbox_created_at ON outbox_events(created_at);
