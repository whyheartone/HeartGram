package com.exteragram.messenger.pillstack.ui.pills.crypto.utils;

import java.util.Locale;

import app.exteraless.pillstack.PillCurrencies;

public final class PillStackCurrencies {

    public static final String TARGET_AUTO = PillCurrencies.AUTO;

    private PillStackCurrencies() {
    }

    public static String resolveTargetCurrency(int account, String selection) {
        String normalized = PillCurrencies.normalize(selection);
        if (TARGET_AUTO.equals(normalized) || normalized.isEmpty()) {
            try {
                java.util.Currency local = java.util.Currency.getInstance(Locale.getDefault());
                if (local != null && !local.getCurrencyCode().isEmpty()) {
                    return local.getCurrencyCode();
                }
            } catch (Throwable ignore) {
            }
            return "USD";
        }
        return normalized;
    }
}
