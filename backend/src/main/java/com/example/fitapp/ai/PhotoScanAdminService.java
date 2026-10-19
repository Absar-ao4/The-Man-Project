package com.example.fitapp.ai;

import com.example.fitapp.ai.PhotoScanDtos.DayStats;
import com.example.fitapp.ai.PhotoScanDtos.PhotoScanConfigDto;
import com.example.fitapp.ai.PhotoScanDtos.PhotoScanStats;
import com.example.fitapp.common.ApiException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Settings and statistics behind the admin endpoints. */
@Service
public class PhotoScanAdminService {

    private final PhotoScanConfigService config;
    private final AiBudgetService budget;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PhotoScanAdminService(PhotoScanConfigService config, AiBudgetService budget, JdbcTemplate jdbc, Clock clock) {
        this.config = config;
        this.budget = budget;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public PhotoScanConfigDto getConfig() {
        return PhotoScanConfigDto.of(config.get());
    }

    public PhotoScanConfigDto update(PhotoScanConfigDto dto) {
        if (dto.freeDailyLimit() < 0 || dto.freeDailyLimit() > 1000) {
            throw ApiException.validation("freeDailyLimit", "must be between 0 and 1000");
        }
        if (dto.premiumDailyLimit() < 0 || dto.premiumDailyLimit() > 1000) {
            throw ApiException.validation("premiumDailyLimit", "must be between 0 and 1000");
        }
        if (dto.globalDailyBudgetUsd() == null || dto.globalDailyBudgetUsd().signum() < 0 || dto.globalDailyBudgetUsd().compareTo(new BigDecimal("100000")) > 0) {
            throw ApiException.validation("globalDailyBudgetUsd", "must be between 0 and 100000");
        }
        if (blank(dto.primaryModel())) {
            throw ApiException.validation("primaryModel", "is required");
        }
        if (blank(dto.fallbackModel())) {
            throw ApiException.validation("fallbackModel", "is required");
        }
        if (dto.escalateBelowConfidence() < 0 || dto.escalateBelowConfidence() > 1) {
            throw ApiException.validation("escalateBelowConfidence", "must be between 0 and 1");
        }
        return PhotoScanConfigDto.of(
                config.update(
                        new PhotoScanConfig(
                                dto.enabled(),
                                dto.freeDailyLimit(),
                                dto.premiumDailyLimit(),
                                dto.globalDailyBudgetUsd(),
                                dto.primaryModel().trim(),
                                dto.fallbackModel().trim(),
                                dto.escalateBelowConfidence())));
    }

    public PhotoScanStats stats() {
        LocalDate today = budget.today();
        java.time.Instant dayStart = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        int calls =
                jdbc.queryForObject("select coalesce(max(calls), 0) from photo_scan_budget where day = ?", Integer.class, today);
        int scans =
                jdbc.queryForObject(
                        "select count(*) from photo_scan_logs where kind = 'PHOTO' and status = 'OK' and created_at >= ?",
                        Integer.class,
                        java.sql.Timestamp.from(dayStart));
        int fallback =
                jdbc.queryForObject(
                        "select count(*) from photo_scan_logs where kind = 'PHOTO' and status = 'OK' and fallback_used and created_at >= ?",
                        Integer.class,
                        java.sql.Timestamp.from(dayStart));
        Double avg =
                jdbc.queryForObject(
                        "select avg(avg_confidence)::float8 from photo_scan_logs where kind = 'PHOTO' and status = 'OK' and created_at >= ?",
                        Double.class,
                        java.sql.Timestamp.from(dayStart));
        List<DayStats> week =
                jdbc.query(
                        "select b.day, b.spend_usd, b.calls,"
                                + " (select count(*) from photo_scan_logs l where l.kind = 'PHOTO' and l.status = 'OK'"
                                + "   and (l.created_at at time zone 'UTC')::date = b.day) as scans"
                                + " from photo_scan_budget b where b.day > ? order by b.day desc",
                        (rs, i) ->
                                new DayStats(
                                        rs.getObject("day", LocalDate.class),
                                        rs.getBigDecimal("spend_usd"),
                                        rs.getInt("calls"),
                                        rs.getInt("scans")),
                        today.minusDays(7));
        return new PhotoScanStats(
                config.get().globalDailyBudgetUsd(), budget.spentToday(), calls, scans, fallback, avg, week);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
