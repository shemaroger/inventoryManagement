CREATE TABLE suppliers (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(150) NOT NULL,
    contact_person  VARCHAR(150),
    phone           VARCHAR(30),
    email           VARCHAR(150),
    address         VARCHAR(255),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE purchase_orders (
    id            BIGSERIAL PRIMARY KEY,
    supplier_id   BIGINT NOT NULL REFERENCES suppliers(id),
    warehouse_id  BIGINT NOT NULL REFERENCES warehouses(id),
    status        VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    order_date    DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE purchase_order_lines (
    id                 BIGSERIAL PRIMARY KEY,
    purchase_order_id  BIGINT NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
    product_id         BIGINT NOT NULL REFERENCES products(id),
    quantity_ordered   NUMERIC(18,2) NOT NULL,
    quantity_received  NUMERIC(18,2) NOT NULL DEFAULT 0,
    unit_cost          NUMERIC(18,2) NOT NULL DEFAULT 0
);

CREATE TABLE supplier_payments (
    id                 BIGSERIAL PRIMARY KEY,
    supplier_id        BIGINT NOT NULL REFERENCES suppliers(id),
    purchase_order_id  BIGINT REFERENCES purchase_orders(id),
    amount             NUMERIC(18,2) NOT NULL,
    payment_date       DATE NOT NULL DEFAULT CURRENT_DATE,
    notes              VARCHAR(500),
    created_at         TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_po_supplier ON purchase_orders(supplier_id);
CREATE INDEX idx_po_warehouse ON purchase_orders(warehouse_id);
CREATE INDEX idx_po_status ON purchase_orders(status);
CREATE INDEX idx_pol_po ON purchase_order_lines(purchase_order_id);
CREATE INDEX idx_pol_product ON purchase_order_lines(product_id);
CREATE INDEX idx_sp_supplier ON supplier_payments(supplier_id);
CREATE INDEX idx_sp_po ON supplier_payments(purchase_order_id);
