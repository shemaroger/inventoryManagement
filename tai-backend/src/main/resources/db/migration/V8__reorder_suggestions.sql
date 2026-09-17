CREATE TABLE reorder_suggestions (
    id                       BIGSERIAL PRIMARY KEY,
    product_id               BIGINT NOT NULL REFERENCES products(id),
    warehouse_id             BIGINT NOT NULL REFERENCES warehouses(id),
    current_quantity         NUMERIC(18,2) NOT NULL,
    reorder_level            NUMERIC(18,2) NOT NULL,
    suggested_quantity       NUMERIC(18,2) NOT NULL,
    urgency_score            NUMERIC(12,2) NOT NULL,
    projected_stockout_date  DATE,
    status                   VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    purchase_order_id        BIGINT REFERENCES purchase_orders(id),
    dismiss_reason           VARCHAR(500),
    created_at               TIMESTAMP NOT NULL DEFAULT now(),
    updated_at               TIMESTAMP NOT NULL DEFAULT now(),
    resolved_at              TIMESTAMP
);

-- No unique constraint on (product_id, warehouse_id): a product can legitimately cycle through
-- OPEN -> RESOLVED -> OPEN again over time as stock rises and falls. The application layer
-- enforces "at most one OPEN row per product/warehouse" by looking up and updating in place.
CREATE INDEX idx_reorder_suggestions_lookup ON reorder_suggestions(product_id, warehouse_id, status);
CREATE INDEX idx_reorder_suggestions_status ON reorder_suggestions(status);
