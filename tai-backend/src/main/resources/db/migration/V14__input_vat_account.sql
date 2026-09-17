-- Input VAT (reclaimable VAT paid to suppliers) is an asset, distinct from VAT Payable (2100,
-- output VAT owed to RRA on sales) which was already seeded. Net VAT payable for a period is
-- VAT Payable's movement minus Input VAT's movement — see ReportService.vatReport().
INSERT INTO accounts (code, name, account_type) VALUES
('1150', 'Input VAT', 'ASSET');
