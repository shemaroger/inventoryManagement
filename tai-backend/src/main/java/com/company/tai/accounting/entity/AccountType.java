package com.company.tai.accounting.entity;

public enum AccountType {
    ASSET,
    LIABILITY,
    EQUITY,
    REVENUE,
    EXPENSE;

    // Assets and Expenses increase with debits (debit-normal); Liabilities, Equity, and Revenue
    // increase with credits (credit-normal). Getting this backwards is the classic way to
    // silently corrupt every ledger and report built on top of it.
    public boolean isDebitNormal() {
        return this == ASSET || this == EXPENSE;
    }
}
