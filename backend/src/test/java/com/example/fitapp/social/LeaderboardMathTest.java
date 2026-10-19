package com.example.fitapp.social;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fitapp.social.LeaderboardMath.LiftPoint;
import com.example.fitapp.social.LeaderboardMath.Scored;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LeaderboardMathTest {

    // ------------------------------------------------------------ weeks

    @Test
    void anIsoWeekIsWrittenAndReadBack() {
        assertThat(LeaderboardMath.isoWeek(LocalDate.of(2026, 3, 9))).isEqualTo("2026-W11");
        assertThat(LeaderboardMath.monday("2026-W11")).isEqualTo(LocalDate.of(2026, 3, 9));
        assertThat(LeaderboardMath.monday("2026-W01")).isEqualTo(LocalDate.of(2025, 12, 29)); // week 1 can start in the old year
        assertThat(LeaderboardMath.isoWeek(LocalDate.of(2025, 12, 29))).isEqualTo("2026-W01");
        assertThat(LeaderboardMath.isoWeek(LocalDate.of(2027, 1, 3))).isEqualTo("2026-W53");
        assertThat(LeaderboardMath.monday("2026-W53")).isEqualTo(LocalDate.of(2026, 12, 28));
    }

    @Test
    void anInvalidWeekIsNull() {
        assertThat(LeaderboardMath.monday(null)).isNull();
        assertThat(LeaderboardMath.monday("")).isNull();
        assertThat(LeaderboardMath.monday("2026-11")).isNull();
        assertThat(LeaderboardMath.monday("2026-W00")).isNull();
        assertThat(LeaderboardMath.monday("2026-W54")).isNull();
        assertThat(LeaderboardMath.monday("2025-W53")).as("2025 has only 52 weeks").isNull();
        assertThat(LeaderboardMath.monday("1999-W10")).isNull();
    }

    @Test
    void aWeekStartsAtMondayMidnightInEachUsersOwnZone() {
        LocalDate monday = LocalDate.of(2026, 3, 9);

        Instant[] utc = LeaderboardMath.window(monday, ZoneId.of("UTC"));
        Instant[] tokyo = LeaderboardMath.window(monday, ZoneId.of("Asia/Tokyo"));
        Instant[] newYork = LeaderboardMath.window(monday, ZoneId.of("America/New_York"));

        assertThat(utc[0]).isEqualTo(Instant.parse("2026-03-09T00:00:00Z"));
        assertThat(utc[1]).isEqualTo(Instant.parse("2026-03-16T00:00:00Z"));
        assertThat(tokyo[0]).isEqualTo(Instant.parse("2026-03-08T15:00:00Z"));
        // New York moved to daylight time on Sunday 8 March 2026: Monday 00:00 is 04:00 UTC and the week is still 7 days of local time.
        assertThat(newYork[0]).isEqualTo(Instant.parse("2026-03-09T04:00:00Z"));
        assertThat(newYork[1]).isEqualTo(Instant.parse("2026-03-16T04:00:00Z"));
    }

    @Test
    void aSundayNightWorkoutBelongsToTheWeekInTheUsersOwnZone() {
        // 23:30 on Sunday 15 March in Tokyo is still week 11 there, but it is already Monday 03:30 in Auckland, so it counts for the next week there.
        Instant workout = Instant.parse("2026-03-15T14:30:00Z");
        LocalDate monday = LocalDate.of(2026, 3, 9);

        Instant[] tokyo = LeaderboardMath.window(monday, ZoneId.of("Asia/Tokyo"));
        Instant[] auckland = LeaderboardMath.window(monday, ZoneId.of("Pacific/Auckland"));

        assertThat(workout).isBetween(tokyo[0], tokyo[1].minusNanos(1));
        assertThat(workout).as("already Monday in Auckland").isAfterOrEqualTo(auckland[1]);
    }

    // ------------------------------------------------------------ ranking

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();
    private final UUID d = UUID.randomUUID();

    @Test
    void ranksAreCompetitionRanksWithSharedPlacesForTies() {
        Map<UUID, Integer> ranks = LeaderboardMath.ranks(List.of(new Scored(a, 50.0), new Scored(b, 80.0), new Scored(c, 80.0), new Scored(d, 10.0)));

        assertThat(ranks.get(b)).isEqualTo(1);
        assertThat(ranks.get(c)).isEqualTo(1);
        assertThat(ranks.get(a)).isEqualTo(3);
        assertThat(ranks.get(d)).isEqualTo(4);
    }

    @Test
    void someoneWithoutDataGetsNoRank() {
        Map<UUID, Integer> ranks = LeaderboardMath.ranks(List.of(new Scored(a, null), new Scored(b, 0.0)));

        assertThat(ranks).containsOnlyKeys(b);
        assertThat(ranks.get(b)).isEqualTo(1);
        assertThat(LeaderboardMath.ranks(List.of())).isEmpty();
    }

    // ------------------------------------------------------------ consistency

    @Test
    void consistencyIsPlannedDaysDoneOverFourWeeks() {
        LocalDate today = LocalDate.of(2026, 3, 10);
        // 3 planned days a week for 4 weeks is 12; 9 done is 75 percent.
        Set<LocalDate> days = Set.of(
                today.minusDays(1), today.minusDays(3), today.minusDays(5), today.minusDays(8), today.minusDays(10),
                today.minusDays(12), today.minusDays(15), today.minusDays(18), today.minusDays(20));

        assertThat(LeaderboardMath.consistencyPct(days, today, 3)).isEqualTo(75.0);
    }

    @Test
    void consistencyIsCappedAt100AndIgnoresDaysOutsideTheWindow() {
        LocalDate today = LocalDate.of(2026, 3, 10);
        Set<LocalDate> everyDay = new java.util.HashSet<>();
        for (int i = 0; i < 28; i++) {
            everyDay.add(today.minusDays(i));
        }
        everyDay.add(today.minusDays(40));
        everyDay.add(today.plusDays(2));

        assertThat(LeaderboardMath.consistencyPct(everyDay, today, 3)).isEqualTo(100.0);
        assertThat(LeaderboardMath.consistencyPct(Set.of(today.minusDays(28), today.plusDays(1)), today, 3)).isZero();
    }

    @Test
    void withNoPlanTheAssumedThreeDaysApply() {
        LocalDate today = LocalDate.of(2026, 3, 10);
        Set<LocalDate> days = Set.of(today, today.minusDays(2), today.minusDays(4), today.minusDays(6), today.minusDays(8), today.minusDays(10));

        assertThat(LeaderboardMath.consistencyPct(days, today, 0)).isEqualTo(50.0);
        assertThat(LeaderboardMath.consistencyPct(days, today, 6)).isEqualTo(25.0);
    }

    // ------------------------------------------------------------ improvement

    private static final Instant END = Instant.parse("2026-03-10T00:00:00Z");
    private final UUID bench = UUID.randomUUID();
    private final UUID squat = UUID.randomUUID();
    private final UUID row = UUID.randomUUID();
    private final UUID curl = UUID.randomUUID();

    private static LiftPoint lift(UUID exercise, double e1rm, int daysBeforeEnd) {
        return new LiftPoint(exercise, e1rm, END.minusSeconds(daysBeforeEnd * 86_400L));
    }

    @Test
    void improvementIsTheAverageChangeOfTheBestOneRepMax() {
        List<LiftPoint> points =
                List.of(
                        lift(bench, 100, 40), lift(bench, 105, 10), // +5 percent
                        lift(squat, 150, 35), lift(squat, 165, 5)); // +10 percent

        assertThat(LeaderboardMath.improvementPct(points, END)).isEqualTo(7.5);
    }

    @Test
    void aLiftWithDataInOnlyOnePeriodIsNotCounted() {
        List<LiftPoint> points = List.of(lift(bench, 100, 40), lift(bench, 110, 5), lift(squat, 150, 3));

        assertThat(LeaderboardMath.improvementPct(points, END)).isEqualTo(10.0);
        assertThat(LeaderboardMath.improvementPct(List.of(lift(squat, 150, 3)), END)).isNull();
        assertThat(LeaderboardMath.improvementPct(List.of(), END)).isNull();
    }

    @Test
    void aDropCountsAsNegativeImprovement() {
        List<LiftPoint> points = List.of(lift(bench, 100, 40), lift(bench, 90, 5));

        assertThat(LeaderboardMath.improvementPct(points, END)).isEqualTo(-10.0);
    }

    @Test
    void onlyTheThreeMostTrainedLiftsCount() {
        List<LiftPoint> points = new java.util.ArrayList<>();
        // bench, squat and row each have 4 sets (+10 percent); the curl has 2 sets and a huge gain that must not count.
        for (UUID exercise : List.of(bench, squat, row)) {
            points.add(lift(exercise, 100, 45));
            points.add(lift(exercise, 100, 40));
            points.add(lift(exercise, 110, 6));
            points.add(lift(exercise, 108, 4));
        }
        points.add(lift(curl, 20, 40));
        points.add(lift(curl, 40, 4));

        assertThat(LeaderboardMath.improvementPct(points, END)).isEqualTo(10.0);
    }

    @Test
    void setsOutsideTheEightWeekWindowAreIgnored() {
        List<LiftPoint> points = List.of(lift(bench, 50, 70), lift(bench, 100, 40), lift(bench, 110, 5), lift(bench, 500, -3));

        assertThat(LeaderboardMath.improvementPct(points, END)).isEqualTo(10.0);
    }
}
