package com.example.fitapp.ai;

import java.math.BigDecimal;

/** The editable settings of the AI features (one row in {@code photo_scan_config}). */
public record PhotoScanConfig(
        boolean enabled,
        int freeDailyLimit,
        int premiumDailyLimit,
        BigDecimal globalDailyBudgetUsd,
        String primaryModel,
        String fallbackModel,
        double escalateBelowConfidence) {}
