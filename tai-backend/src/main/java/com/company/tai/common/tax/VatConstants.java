package com.company.tai.common.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;

// Single source of truth for Rwanda's standard 18% VAT rate — every VAT calculation in the app
// (Sales, Purchasing, JournalService, reporting) reads from here rather than duplicating the
// rate, so a future rate change or exemption model only has to touch one place.
public final class VatConstants {

    public static final BigDecimal RATE = new BigDecimal("0.18");

    private VatConstants() {}

    public static BigDecimal vatOn(BigDecimal netAmount) {
        return netAmount.multiply(RATE).setScale(2, RoundingMode.HALF_UP);
    }
}
