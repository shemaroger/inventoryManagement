ALTER TABLE journal_entry_lines ADD COLUMN reconciled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE journal_entry_lines ADD COLUMN reconciled_date DATE;
