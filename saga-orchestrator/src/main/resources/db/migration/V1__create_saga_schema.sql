-- V1__create_saga_schema.sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE saga_instances (
    saga_id        VARCHAR(255) PRIMARY KEY,
    order_id       VARCHAR(255) NOT NULL,
    customer_id    VARCHAR(255) NOT NULL,
    status         VARCHAR(50)  NOT NULL DEFAULT 'STARTED',
    current_step   VARCHAR(100) NOT NULL,
    saga_data      JSONB,
    failure_reason TEXT,
    retry_count    INTEGER      NOT NULL DEFAULT 0,
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT now(),
    completed_at   TIMESTAMP
);

CREATE UNIQUE INDEX idx_saga_order_id  ON saga_instances(order_id);
CREATE INDEX        idx_saga_status    ON saga_instances(status);
CREATE INDEX        idx_saga_step      ON saga_instances(current_step);
CREATE INDEX        idx_saga_updated   ON saga_instances(updated_at);

CREATE TABLE saga_events (
    id          UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    saga_id     VARCHAR(255) NOT NULL REFERENCES saga_instances(saga_id),
    event_type  VARCHAR(255) NOT NULL,
    payload     JSONB,
    occurred_at TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_saga_events_saga ON saga_events(saga_id);
