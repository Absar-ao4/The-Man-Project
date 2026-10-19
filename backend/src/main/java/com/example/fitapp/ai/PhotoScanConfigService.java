package com.example.fitapp.ai;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Reads the AI settings, cached for five minutes, and saves changes made through the admin API. */
@Service
public class PhotoScanConfigService {

    static final Duration TTL = Duration.ofMinutes(5);

    private record Cached(PhotoScanConfig config, Instant loadedAt) {}

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final AtomicReference<Cached> cache = new AtomicReference<>();

    public PhotoScanConfigService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public PhotoScanConfig get() {
        Cached cached = cache.get();
        Instant now = clock.instant();
        if (cached != null && Duration.between(cached.loadedAt(), now).compareTo(TTL) < 0) {
            return cached.config();
        }
        PhotoScanConfig fresh =
                jdbc.queryForObject(
                        "select enabled, free_daily_limit, premium_daily_limit, global_daily_budget_usd,"
                                + " primary_model, fallback_model, escalate_below_confidence"
                                + " from photo_scan_config where id = 1",
                        (rs, i) ->
                                new PhotoScanConfig(
                                        rs.getBoolean("enabled"),
                                        rs.getInt("free_daily_limit"),
                                        rs.getInt("premium_daily_limit"),
                                        rs.getBigDecimal("global_daily_budget_usd"),
                                        rs.getString("primary_model"),
                                        rs.getString("fallback_model"),
                                        rs.getDouble("escalate_below_confidence")));
        cache.set(new Cached(fresh, now));
        return fresh;
    }

    /** Saves new settings. The change is visible to this server at once; other instances pick it up within five minutes. */
    public PhotoScanConfig update(PhotoScanConfig config) {
        jdbc.update(
                "update photo_scan_config set enabled = ?, free_daily_limit = ?, premium_daily_limit = ?,"
                        + " global_daily_budget_usd = ?, primary_model = ?, fallback_model = ?,"
                        + " escalate_below_confidence = ?, updated_at = now() where id = 1",
                config.enabled(),
                config.freeDailyLimit(),
                config.premiumDailyLimit(),
                config.globalDailyBudgetUsd(),
                config.primaryModel(),
                config.fallbackModel(),
                config.escalateBelowConfidence());
        cache.set(null);
        return get();
    }
}
