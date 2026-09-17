CREATE TABLE stock_anomalies (
    id               BIGSERIAL PRIMARY KEY,
    adjustment_id    BIGINT REFERENCES stock_adjustments(id),
    sale_id          BIGINT, -- no FK yet: no Sales module exists in this codebase; placeholder for when it does
    product_id       BIGINT NOT NULL REFERENCES products(id),
    warehouse_id     BIGINT REFERENCES warehouses(id),
    anomaly_type     VARCHAR(30) NOT NULL,
    severity_score   NUMERIC(8,2) NOT NULL,
    context_note     VARCHAR(500),
    detected_at      TIMESTAMP NOT NULL DEFAULT now(),
    reviewed_by      BIGINT REFERENCES users(id),
    reviewed_at      TIMESTAMP,
    review_note      VARCHAR(500),
    status           VARCHAR(20) NOT NULL DEFAULT 'OPEN'
);

CREATE INDEX idx_stock_anomalies_status ON stock_anomalies(status);
CREATE INDEX idx_stock_anomalies_product ON stock_anomalies(product_id);
CREATE INDEX idx_stock_anomalies_adjustment ON stock_anomalies(adjustment_id);
