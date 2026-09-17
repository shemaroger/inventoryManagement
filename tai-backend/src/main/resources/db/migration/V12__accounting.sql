CREATE TABLE accounts (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(20) NOT NULL UNIQUE,
    name                VARCHAR(150) NOT NULL,
    account_type        VARCHAR(20) NOT NULL,
    parent_account_id   BIGINT REFERENCES accounts(id),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE journal_entries (
    id            BIGSERIAL PRIMARY KEY,
    entry_date    DATE NOT NULL,
    description   VARCHAR(500) NOT NULL,
    reference     VARCHAR(200),
    source_type   VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    source_id     BIGINT,
    created_by    BIGINT REFERENCES users(id),
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE journal_entry_lines (
    id                  BIGSERIAL PRIMARY KEY,
    journal_entry_id    BIGINT NOT NULL REFERENCES journal_entries(id) ON DELETE CASCADE,
    account_id          BIGINT NOT NULL REFERENCES accounts(id),
    debit_amount        NUMERIC(18,2) NOT NULL DEFAULT 0,
    credit_amount       NUMERIC(18,2) NOT NULL DEFAULT 0
);

CREATE INDEX idx_je_date ON journal_entries(entry_date);
CREATE INDEX idx_je_source ON journal_entries(source_type, source_id);
CREATE INDEX idx_jel_entry ON journal_entry_lines(journal_entry_id);
CREATE INDEX idx_jel_account ON journal_entry_lines(account_id);

-- Default Chart of Accounts for a construction materials trading business, perpetual inventory
-- method (Inventory is an asset touched directly at purchase-receipt and sale time — no
-- separate "Purchases" expense account, since that would be periodic-method bookkeeping and
-- would conflict with how JournalService posts PO receipts and sales below).
INSERT INTO accounts (code, name, account_type) VALUES
('1000', 'Cash', 'ASSET'),
('1010', 'Bank', 'ASSET'),
('1100', 'Accounts Receivable', 'ASSET'),
('1200', 'Inventory', 'ASSET'),
('1500', 'Fixed Assets', 'ASSET'),
('2000', 'Accounts Payable', 'LIABILITY'),
('2100', 'VAT Payable', 'LIABILITY'),
('2200', 'Accrued Expenses', 'LIABILITY'),
('3000', 'Owner''s Equity', 'EQUITY'),
('3100', 'Retained Earnings', 'EQUITY'),
('4000', 'Sales Revenue', 'REVENUE'),
('5000', 'Cost of Goods Sold', 'EXPENSE'),
('5100', 'Operating Expenses', 'EXPENSE');
