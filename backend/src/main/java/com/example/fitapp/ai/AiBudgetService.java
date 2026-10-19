package com.example.fitapp.ai;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** The one daily money budget every AI call counts against, whatever feature made it. UTC days. */
@Service
public class AiBudgetService {

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final PhotoScanConfigService config;

    public AiBudgetService(JdbcTemplate jdbc, Clock clock, PhotoScanConfigService config) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.config = config;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }

    public BigDecimal spentToday() {
        BigDecimal spent =
                jdbc.queryForObject(
                        "select coalesce(max(spend_usd), 0) from photo_scan_budget where day = ?", BigDecimal.class, today());
        return spent == null ? BigDecimal.ZERO : spent;
    }

    /** True once today's spend has reached the budget: new AI calls are refused until tomorrow or a higher budget. */
    public boolean exhausted() {
        return spentToday().compareTo(config.get().globalDailyBudgetUsd()) >= 0;
    }

    /** Adds one call's cost. Atomic, so concurrent calls never lose an update. */
    public void add(BigDecimal costUsd) {
        jdbc.update(
                "insert into photo_scan_budget (day, spend_usd, calls) values (?, ?, 1)"
                        + " on conflict (day) do update set spend_usd = photo_scan_budget.spend_usd + excluded.spend_usd,"
                        + " calls = photo_scan_budget.calls + 1",
                today(),
                costUsd);
    }
}
