package com.example.fitapp.ai;

import com.example.fitapp.config.AiPricingProperties;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Per user per day limits for AI features, one counter per feature name. Features added from Phase 10 on use
 * this (photo scans and plan explanations from earlier phases keep their own tables). The limits are in
 * application.yml (app.ai.daily-limits.&lt;feature&gt;).
 */
@Service
public class AiQuotaService {

    public static final String DIET_ARRANGE = "diet-arrange";
    public static final String MEAL_SUGGESTIONS = "meal-suggestions";
    public static final String WEEKLY_SUMMARY = "weekly-summary";

    private final JdbcTemplate jdbc;
    private final AiBudgetService budget;
    private final AiPricingProperties props;

    public AiQuotaService(JdbcTemplate jdbc, AiBudgetService budget, AiPricingProperties props) {
        this.jdbc = jdbc;
        this.budget = budget;
        this.props = props;
    }

    public int limit(String feature) {
        return props.dailyLimitFor(feature);
    }

    /** Takes one use if any are left today. One atomic statement, so two requests can not both take the last one. */
    public boolean tryConsume(UUID userId, String feature) {
        int limit = limit(feature);
        if (limit <= 0) {
            return false;
        }
        int rows =
                jdbc.update(
                        "insert into ai_feature_usage (user_id, day, feature, count) values (?, ?, ?, 1)"
                                + " on conflict (user_id, day, feature) do update set count = ai_feature_usage.count + 1"
                                + " where ai_feature_usage.count < ?",
                        userId,
                        budget.today(),
                        feature,
                        limit);
        return rows == 1;
    }

    /** Gives a use back after a call that produced nothing. */
    public void refund(UUID userId, String feature) {
        jdbc.update(
                "update ai_feature_usage set count = count - 1 where user_id = ? and day = ? and feature = ? and count > 0",
                userId,
                budget.today(),
                feature);
    }

    public int used(UUID userId, String feature) {
        Integer count =
                jdbc.queryForObject(
                        "select coalesce(max(count), 0) from ai_feature_usage where user_id = ? and day = ? and feature = ?",
                        Integer.class,
                        userId,
                        budget.today(),
                        feature);
        return count == null ? 0 : count;
    }
}
