package com.example.fitapp.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RateLimitFilterTest {

    @Test
    void theEndpointsThatCostMoneyOrDoHeavyWorkAreTheExpensiveOnes() {
        assertThat(RateLimitFilter.isExpensive("/api/ai/meal-suggestions", "POST")).isTrue();
        assertThat(RateLimitFilter.isExpensive("/api/ai/workout-recommendations", "GET")).isTrue();
        assertThat(RateLimitFilter.isExpensive("/api/nutrition/photo-scan", "POST")).isTrue();
        assertThat(RateLimitFilter.isExpensive("/api/plans/generate/explain", "POST")).isTrue();
        assertThat(RateLimitFilter.isExpensive("/api/me/export", "GET")).isTrue();
        assertThat(RateLimitFilter.isExpensive("/api/diet/plans", "POST")).isTrue();
    }

    @Test
    void everyDayEndpointsAreNot() {
        assertThat(RateLimitFilter.isExpensive("/api/diet/plans", "GET")).as("only building a plan is expensive").isFalse();
        assertThat(RateLimitFilter.isExpensive("/api/plans/generate", "POST")).as("the rules based generator is cheap").isFalse();
        assertThat(RateLimitFilter.isExpensive("/api/sync/push", "POST")).isFalse();
        assertThat(RateLimitFilter.isExpensive("/api/articles", "GET")).isFalse();
        assertThat(RateLimitFilter.isExpensive("/api/me/profile", "GET")).isFalse();
    }

    @Test
    void anUnsetExpensiveLimitFallsBackToThirty() {
        assertThat(new RateLimitProperties(10, 30, 0).expensivePerMinute()).isEqualTo(30);
        assertThat(new RateLimitProperties(10, 30, -5).expensivePerMinute()).isEqualTo(30);
        assertThat(new RateLimitProperties(10, 30, 7).expensivePerMinute()).isEqualTo(7);
    }
}
