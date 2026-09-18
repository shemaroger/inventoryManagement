CREATE TABLE company_settings (
    id BIGINT PRIMARY KEY,
    legal_name VARCHAR(150) NOT NULL,
    trading_name VARCHAR(150),
    tin_number VARCHAR(30),
    registration_number VARCHAR(50),
    address_line VARCHAR(200),
    city VARCHAR(100),
    country VARCHAR(100),
    phone VARCHAR(30),
    email VARCHAR(150),
    website VARCHAR(150),
    logo_url VARCHAR(255),
    updated_at TIMESTAMP
);

-- Singleton row (id=1) seeded with what's already known about this business from elsewhere in
-- the app (logo, name, tagline) — an admin can fill in the rest (TIN, registration number,
-- address) via the Company Profile settings page.
INSERT INTO company_settings (id, legal_name, trading_name, city, country, logo_url, updated_at)
VALUES (1, 'Mapleco S.A.R.L', 'Mapleco', 'Kigali', 'Rwanda', '/logo.png', now());
