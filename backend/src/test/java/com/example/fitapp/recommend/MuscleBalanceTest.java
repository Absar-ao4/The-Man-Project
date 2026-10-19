package com.example.fitapp.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fitapp.recommend.MuscleBalance.Read;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MuscleBalanceTest {

    private static Map<String, Integer> sets(Object... pairs) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return m;
    }

    @Test
    void everyMuscleIsListedInAFixedOrderAndTotalsAddUp() {
        Map<String, Integer> all = MuscleBalance.complete(sets("QUADS", 4, "CHEST", 6));

        assertThat(all.keySet()).containsExactlyElementsOf(MuscleBalance.MUSCLES);
        assertThat(all.get("CALVES")).isZero();
        assertThat(MuscleBalance.total(all)).isEqualTo(10);
    }

    @Test
    void anEmptyWeekSaysSoAndHasNoTips() {
        Read r = MuscleBalance.read(MuscleBalance.complete(Map.of()));

        assertThat(r.summary()).contains("No sets were finished");
        assertThat(r.tips()).isEmpty();
    }

    @Test
    void aLightWeekIsCalledLight() {
        Read r = MuscleBalance.read(MuscleBalance.complete(sets("CHEST", 3, "BACK", 2)));

        assertThat(r.summary()).contains("5 sets").contains("3 pushing").contains("2 pulling").contains("light week");
        assertThat(r.tips()).isEmpty();
    }

    @Test
    void aBalancedWeekHasNoTips() {
        Read r =
                MuscleBalance.read(
                        MuscleBalance.complete(sets("CHEST", 4, "SHOULDERS", 2, "TRICEPS", 2, "BACK", 5, "BICEPS", 3, "QUADS", 4, "HAMSTRINGS", 2, "GLUTES", 2, "CORE", 3)));

        assertThat(r.summary()).contains("27 sets").contains("8 pushing, 8 pulling and 8 for legs").endsWith("That is a well balanced week.");
        assertThat(r.tips()).isEmpty();
    }

    @Test
    void aLowMuscleGroupIsNamedWithItsShare() {
        Read r = MuscleBalance.read(MuscleBalance.complete(sets("CHEST", 6, "SHOULDERS", 3, "TRICEPS", 3, "BACK", 5, "BICEPS", 3, "QUADS", 2, "CORE", 1)));

        assertThat(r.tips()).hasSize(1);
        assertThat(r.tips().get(0)).startsWith("Legs got only 2 of 23 sets (9 percent)");
        assertThat(r.summary()).doesNotContain("well balanced");
    }

    @Test
    void pushingFarMoreThanPullingIsPointedOut() {
        Read r = MuscleBalance.read(MuscleBalance.complete(sets("CHEST", 12, "SHOULDERS", 4, "TRICEPS", 4, "BACK", 6, "QUADS", 10, "CORE", 2)));

        assertThat(r.tips()).anyMatch(t -> t.contains("pushed 20 sets and pulled 6"));
    }

    @Test
    void pullingFarMoreThanPushingIsPointedOut() {
        Read r = MuscleBalance.read(MuscleBalance.complete(sets("CHEST", 6, "BACK", 12, "BICEPS", 6, "QUADS", 8, "CORE", 2)));

        assertThat(r.tips()).anyMatch(t -> t.contains("pulled 18 sets and pushed 6"));
    }

    @Test
    void noCoreWorkIsMentionedOnlyForRealWeeks() {
        Read real = MuscleBalance.read(MuscleBalance.complete(sets("CHEST", 4, "SHOULDERS", 2, "TRICEPS", 2, "BACK", 5, "BICEPS", 3, "QUADS", 4, "HAMSTRINGS", 2, "GLUTES", 2)));
        Read small = MuscleBalance.read(MuscleBalance.complete(sets("CHEST", 4, "BACK", 3)));

        assertThat(real.tips()).anyMatch(t -> t.contains("No core work"));
        assertThat(small.tips()).isEmpty();
    }

    @Test
    void thereAreNeverMoreThanThreeTips() {
        Read r = MuscleBalance.read(MuscleBalance.complete(sets("CHEST", 20)));

        assertThat(r.tips()).hasSize(3);
    }
}
