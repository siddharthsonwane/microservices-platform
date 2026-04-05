-- V1__create_payments_schema.sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE payments (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id         VARCHAR(255)   NOT NULL,
    saga_id          VARCHAR(255),
    customer_id      VARCHAR(255)   NOT NULL,
    status           VARCHAR(50)    NOT NULL DEFAULT 'PENDING',
    amount           NUMERIC(19, 4) NOT NULL,
    currency         CHAR(3)        NOT NULL DEFAULT 'USD',
    transaction_ref  VARCHAR(255)   UNIQUE,
    gateway_response TEXT,
    failure_reason   TEXT,
    version          BIGINT         NOT NULL DEFAULT 0,
    created_at       TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_payments_saga_id  ON payments(saga_id);
CREATE INDEX idx_payments_status   ON payments(status);
