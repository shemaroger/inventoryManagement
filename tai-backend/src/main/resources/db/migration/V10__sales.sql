CREATE TABLE customers (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(150) NOT NULL,
    contact_person      VARCHAR(150),
    phone               VARCHAR(30),
    email               VARCHAR(150),
    address             VARCHAR(255),
    credit_limit        NUMERIC(18,2) NOT NULL DEFAULT 0,
    current_balance     NUMERIC(18,2) NOT NULL DEFAULT 0,
    customer_category   VARCHAR(50),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE sales (
    id            BIGSERIAL PRIMARY KEY,
    customer_id   BIGINT NOT NULL REFERENCES customers(id),
    warehouse_id  BIGINT NOT NULL REFERENCES warehouses(id),
    status        VARCHAR(30) NOT NULL DEFAULT 'QUOTATION',
    payment_type  VARCHAR(20) NOT NULL DEFAULT 'CASH',
    sale_date     DATE NOT NULL DEFAULT CURRENT_DATE,
    created_by    BIGINT REFERENCES users(id),
    notes         VARCHAR(1000),
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE sale_lines (
    id                 BIGSERIAL PRIMARY KEY,
    sale_id            BIGINT NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
    product_id         BIGINT NOT NULL REFERENCES products(id),
    quantity           NUMERIC(18,2) NOT NULL,
    unit_price         NUMERIC(18,2) NOT NULL DEFAULT 0,
    discount_percent   NUMERIC(5,2) NOT NULL DEFAULT 0
);

CREATE TABLE sale_payments (
    id              BIGSERIAL PRIMARY KEY,
    sale_id         BIGINT NOT NULL REFERENCES sales(id),
    amount          NUMERIC(18,2) NOT NULL,
    payment_date    DATE NOT NULL DEFAULT CURRENT_DATE,
    payment_method  VARCHAR(20) NOT NULL DEFAULT 'CASH',
    notes           VARCHAR(500),
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_sale_customer ON sales(customer_id);
CREATE INDEX idx_sale_warehouse ON sales(warehouse_id);
CREATE INDEX idx_sale_status ON sales(status);
CREATE INDEX idx_sl_sale ON sale_lines(sale_id);
CREATE INDEX idx_sl_product ON sale_lines(product_id);
CREATE INDEX idx_sp_sale ON sale_payments(sale_id);
