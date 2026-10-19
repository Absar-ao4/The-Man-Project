package com.example.fitapp.ai;

import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Writes one row per AI call for the admin statistics. Numbers only: never an image, a prompt or a
 * hint. A failure to write the row is swallowed, because losing a statistic must not fail a user's request.
 */
@Service
public class AiLogService {

    private static final Logger log = LoggerFactory.getLogger(AiLogService.class);

    private final JdbcTemplate jdbc;

    public AiLogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void log(
            UUID userId,
            String kind,
            String model,
            int inputTokens,
            int outputTokens,
            BigDecimal costUsd,
            int items,
            Double averageConfidence,
            boolean fallbackUsed,
            String status,
            long latencyMs) {
        try {
            jdbc.update(
                    "insert into photo_scan_logs (id, user_id, kind, model, input_tokens, output_tokens, cost_usd, items,"
                            + " avg_confidence, fallback_used, status, latency_ms) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(),
                    userId,
                    kind,
                    model,
                    inputTokens,
                    outputTokens,
                    costUsd,
                    items,
                    averageConfidence == null ? null : BigDecimal.valueOf(averageConfidence).setScale(3, java.math.RoundingMode.HALF_UP),
                    fallbackUsed,
                    status,
                    (int) Math.min(latencyMs, Integer.MAX_VALUE));
        } catch (RuntimeException e) {
            log.warn("Could not write an AI log row: {}", e.getClass().getSimpleName());
        }
    }
}
