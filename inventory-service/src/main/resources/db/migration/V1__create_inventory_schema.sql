-- V1__create_inventory_schema.sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE inventory_items (
    id                  UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id          VARCHAR(255) NOT NULL UNIQUE,
    product_name        VARCHAR(500) NOT NULL,
    available_quantity  INTEGER      NOT NULL DEFAULT 0 CHECK (available_quantity >= 0),
    reserved_quantity   INTEGER      NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    version             BIGINT       NOT NULL DEFAULT 0,
    updated_at          TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE inventory_reservations (
    id          UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id    VARCHAR(255) NOT NULL,
    saga_id     VARCHAR(255),
    product_id  VARCHAR(255) NOT NULL,
    quantity    INTEGER      NOT NULL,
    status      VARCHAR(50)  NOT NULL DEFAULT 'RESERVED',
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    released_at TIMESTAMP
);

CREATE INDEX idx_inv_product_id  ON inventory_items(product_id);
CREATE INDEX idx_res_order_id    ON inventory_reservations(order_id);
CREATE INDEX idx_res_saga_id     ON inventory_reservations(saga_id);
CREATE INDEX idx_res_status      ON inventory_reservations(status);

-- Seed some initial stock for testing
INSERT INTO inventory_items (product_id, product_name, available_quantity)
VALUES
    ('PROD-001', 'Laptop Pro 15', 100),
    ('PROD-002', 'Wireless Mouse', 500),
    ('PROD-003', 'USB-C Hub',     250),
    ('PROD-004', 'Mechanical Keyboard', 75),
    ('PROD-005', 'Monitor 27"',    30);
