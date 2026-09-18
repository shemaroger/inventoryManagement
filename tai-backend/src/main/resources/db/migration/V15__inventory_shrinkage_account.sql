-- Absorbs the accounting effect of stock adjustments that aren't already covered by a Sale
-- (DECREASE via completion) or a Purchase Order receipt (INCREASE) — i.e. DAMAGE, LOST,
-- RECOUNT, and any ad-hoc manual correction made via POST /api/stock/adjustments.
INSERT INTO accounts (code, name, account_type) VALUES
('5200', 'Inventory Shrinkage / Write-off', 'EXPENSE');
