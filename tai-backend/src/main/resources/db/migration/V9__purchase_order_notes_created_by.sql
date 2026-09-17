ALTER TABLE purchase_orders ADD COLUMN notes VARCHAR(1000);
ALTER TABLE purchase_orders ADD COLUMN created_by BIGINT REFERENCES users(id);
