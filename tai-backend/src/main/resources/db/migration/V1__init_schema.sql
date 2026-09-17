CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    full_name     VARCHAR(150) NOT NULL,
    email         VARCHAR(150) NOT NULL UNIQUE,
    phone_number  VARCHAR(30),
    password_hash VARCHAR(255) NOT NULL,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE branches (
    id      BIGSERIAL PRIMARY KEY,
    name    VARCHAR(150) NOT NULL,
    address VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE warehouses (
    id         BIGSERIAL PRIMARY KEY,
    branch_id  BIGINT REFERENCES branches(id),
    name       VARCHAR(150) NOT NULL,
    location   VARCHAR(255),
    is_active  BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE categories (
    id        BIGSERIAL PRIMARY KEY,
    name      VARCHAR(150) NOT NULL UNIQUE,
    parent_id BIGINT REFERENCES categories(id)
);

CREATE TABLE brands (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL UNIQUE
);

CREATE TABLE units (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(50) NOT NULL UNIQUE,
    abbreviation   VARCHAR(10) NOT NULL,
    base_unit_id   BIGINT REFERENCES units(id),
    conversion_factor NUMERIC(18,6) DEFAULT 1
);

CREATE TABLE products (
    id           BIGSERIAL PRIMARY KEY,
    sku          VARCHAR(80) NOT NULL UNIQUE,
    barcode      VARCHAR(80) UNIQUE,
    name         VARCHAR(200) NOT NULL,
    description  TEXT,
    category_id  BIGINT REFERENCES categories(id),
    brand_id     BIGINT REFERENCES brands(id),
    unit_id      BIGINT REFERENCES units(id),
    cost_price   NUMERIC(18,2) NOT NULL DEFAULT 0,
    selling_price NUMERIC(18,2) NOT NULL DEFAULT 0,
    reorder_level NUMERIC(18,2) NOT NULL DEFAULT 0,
    is_active    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE stock_items (
    id           BIGSERIAL PRIMARY KEY,
    product_id   BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    warehouse_id BIGINT NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
    quantity     NUMERIC(18,2) NOT NULL DEFAULT 0,
    updated_at   TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (product_id, warehouse_id)
);

CREATE TABLE stock_adjustments (
    id            BIGSERIAL PRIMARY KEY,
    product_id    BIGINT NOT NULL REFERENCES products(id),
    warehouse_id  BIGINT NOT NULL REFERENCES warehouses(id),
    adjustment_type VARCHAR(30) NOT NULL,
    quantity      NUMERIC(18,2) NOT NULL,
    reason        VARCHAR(255),
    performed_by  BIGINT REFERENCES users(id),
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_brand ON products(brand_id);
CREATE INDEX idx_stock_items_product ON stock_items(product_id);
CREATE INDEX idx_stock_items_warehouse ON stock_items(warehouse_id);
CREATE INDEX idx_stock_adjustments_product ON stock_adjustments(product_id);

INSERT INTO roles (name, description) VALUES
    ('ADMIN', 'Full system access'),
    ('MANAGER', 'Manage inventory, sales and reports for assigned branch'),
    ('STAFF', 'Day-to-day operational access');
