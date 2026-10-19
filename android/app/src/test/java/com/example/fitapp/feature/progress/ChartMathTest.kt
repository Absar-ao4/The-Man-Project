package com.example.fitapp.feature.progress

import com.example.fitapp.core.database.StrengthSet
import com.example.fitapp.feature.progress.domain.Achievement
import com.example.fitapp.feature.progress.domain.AchievementKind
import com.example.fitapp.feature.progress.domain.ChartAxis
import com.example.fitapp.feature.progress.domain.ChartMath
import com.example.fitapp.feature.progress.domain.DailyGoals
import com.example.fitapp.feature.progress.domain.ProgressFormat
import com.example.fitapp.feature.progress.domain.ProgressPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ChartMathTest {
    @Test
    fun `epley estimates the one rep max and a single rep is the weight`() {
        assertEquals(100.0, ChartMath.epley(100.0, 1), 0.0)
        assertEquals(116.67, ChartMath.epley(100.0, 5), 0.01)
        assertEquals(133.33, ChartMath.epley(100.0, 10), 0.01)
    }

    @Test
    fun `strength points take the best set of each local day`() {
        val zone = ZoneId.of("America/New_York")
        val sets =
            listOf(
                StrengthSet(100.0, 5, Instant.parse("2026-03-02T01:00:00Z").toEpochMilli()), // Mar 1, 20:00 in New York
                StrengthSet(80.0, 10, Instant.parse("2026-03-02T01:30:00Z").toEpochMilli()),
                StrengthSet(105.0, 5, Instant.parse("2026-03-05T15:00:00Z").toEpochMilli()),
            )

        val points = ChartMath.strengthPoints(sets, zone)

        assertEquals(listOf(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 5)), points.map { it.date })
        assertEquals(116.7, points[0].e1rmKg, 0.0) // 100 x 5 beats 80 x 10 (106.7)
        assertEquals(122.5, points[1].e1rmKg, 0.0)
    }

    @Test
    fun `workouts are counted per local day`() {
        val zone = ZoneId.of("UTC")
        val ends =
            listOf("2026-03-01T10:00:00Z", "2026-03-01T18:00:00Z", "2026-03-03T09:00:00Z").map { Instant.parse(it).toEpochMilli() }

        val byDay = ChartMath.workoutsByDay(ends, zone)

        assertEquals(2, byDay[LocalDate.of(2026, 3, 1)])
        assertEquals(1, byDay[LocalDate.of(2026, 3, 3)])
        assertNull(byDay[LocalDate.of(2026, 3, 2)])
    }

    @Test
    fun `heat levels stop at three`() {
        assertEquals(0, ChartMath.heatLevel(0))
        assertEquals(1, ChartMath.heatLevel(1))
        assertEquals(3, ChartMath.heatLevel(3))
        assertEquals(3, ChartMath.heatLevel(9))
        assertEquals(0, ChartMath.heatLevel(-1))
    }

    @Test
    fun `the heatmap has a column per week, monday first, and shows no future days`() {
        val today = LocalDate.of(2026, 3, 11) // a Wednesday
        val counts = mapOf(LocalDate.of(2026, 3, 10) to 2, LocalDate.of(2026, 3, 3) to 5)

        val grid = ChartMath.heatmapWeeks(counts, today, weeks = 2)

        assertEquals(2, grid.size)
        assertTrue(grid.all { it.size == 7 })
        assertEquals(LocalDate.of(2026, 3, 2), grid[0][0]!!.date) // Monday of the first column
        assertEquals(LocalDate.of(2026, 3, 9), grid[1][0]!!.date)
        assertEquals(3, grid[0][1]!!.level) // Mar 3, five workouts, capped
        assertEquals(5, grid[0][1]!!.count)
        assertEquals(2, grid[1][1]!!.level) // Mar 10
        assertNotNull(grid[1][2]) // today
        assertNull(grid[1][3]) // Thursday has not happened
        assertNull(grid[1][6])
    }

    @Test
    fun `an axis fits the values with room and never has no height`() {
        assertEquals(ChartAxis(0.0, 1.0), ChartMath.axis(emptyList()))
        assertEquals(ChartAxis(4.0, 6.0), ChartMath.axis(listOf(5.0, 5.0)))
        val axis = ChartMath.axis(listOf(70.0, 80.0))
        assertEquals(69.0, axis.min, 1e-9)
        assertEquals(81.0, axis.max, 1e-9)
        assertEquals(0.0, ChartMath.axis(listOf(70.0, 80.0), floorAtZero = true).min, 0.0)
    }

    @Test
    fun `axis fractions run from the bottom to the top and are clamped`() {
        val axis = ChartAxis(0.0, 10.0)

        assertEquals(0f, axis.fraction(0.0), 0f)
        assertEquals(0.5f, axis.fraction(5.0), 0f)
        assertEquals(1f, axis.fraction(10.0), 0f)
        assertEquals(1f, axis.fraction(99.0), 0f)
        assertEquals(0f, axis.fraction(-4.0), 0f)
    }

    @Test
    fun `days without anything are filled with zero and can be bucketed`() {
        val from = LocalDate.of(2026, 3, 1)
        val filled = ChartMath.fillDays(mapOf(from to 10, from.plusDays(2) to 5), from, from.plusDays(4))

        assertEquals(listOf(10, 0, 5, 0, 0), filled.map { it.second })
        assertEquals(5, filled.size)
        assertTrue(ChartMath.fillDays(emptyMap(), from, from.minusDays(1)).isEmpty())
        val buckets = ChartMath.bucket(filled, 2)
        assertEquals(listOf(10, 5, 0), buckets.map { it.second })
        assertEquals(from.plusDays(2), buckets[1].first)
    }
}

class DailyGoalsTest {
    @Test
    fun `everything done is one hundred percent`() {
        assertEquals(1f, DailyGoals.completion(2000.0, 2000, 120.0, 120, 8, 8, true), 0f)
    }

    @Test
    fun `nothing done is zero`() {
        assertEquals(0f, DailyGoals.completion(0.0, 2000, 0.0, 120, 0, 8, false), 0f)
    }

    @Test
    fun `the four parts count equally and each is capped at its target`() {
        // calories 100 percent, protein 50, water 50, no workout: (1 + .5 + .5 + 0) / 4
        assertEquals(0.5f, DailyGoals.completion(2000.0, 2000, 60.0, 120, 4, 8, false), 0.0001f)
        // Eating far over the target does not make up for the rest.
        assertEquals(0.25f, DailyGoals.completion(9000.0, 2000, 0.0, 120, 0, 8, false), 0.0001f)
    }

    @Test
    fun `a part without a target is left out`() {
        assertEquals(0.5f, DailyGoals.completion(2000.0, 2000, 60.0, null, 0, null, false), 0.0001f)
        assertEquals(1f, DailyGoals.completion(0.0, null, 0.0, null, 0, null, true), 0f)
        assertEquals(0f, DailyGoals.completion(0.0, 0, 0.0, 0, 0, 0, false), 0f)
    }

    @Test
    fun `percent text rounds to a whole number`() {
        assertEquals("40%", DailyGoals.percentText(0.4f))
        assertEquals("0%", DailyGoals.percentText(0f))
        assertEquals("100%", DailyGoals.percentText(1f))
    }
}

class ProgressFormatTest {
    @Test
    fun `comparisons say more, fewer or same`() {
        assertEquals("2 more than last week", ProgressFormat.comparison(5, 3, ProgressPeriod.WEEK))
        assertEquals("1 fewer than last month", ProgressFormat.comparison(2, 3, ProgressPeriod.MONTH))
        assertEquals("Same as last week", ProgressFormat.comparison(3, 3, ProgressPeriod.WEEK))
    }

    @Test
    fun `volume has thousands separators in the right unit`() {
        assertEquals("12,450 kg", ProgressFormat.volume(12_450.0, imperial = false))
        assertEquals("27,448 lb", ProgressFormat.volume(12_450.0, imperial = true))
        assertEquals("0 kg", ProgressFormat.volume(0.0, imperial = false))
    }

    @Test
    fun `calories protein and dates read well`() {
        assertEquals("2,150 kcal", ProgressFormat.kcal(2_150.4))
        assertEquals("No data", ProgressFormat.kcal(null))
        assertEquals("130 g", ProgressFormat.grams(130.2))
        assertEquals("No data", ProgressFormat.grams(null))
        assertEquals("Mar 4", ProgressFormat.shortDate(LocalDate.of(2026, 3, 4)))
    }

    @Test
    fun `the nutrition line counts days on target`() {
        assertEquals("No food logged yet", ProgressFormat.nutritionLine(0, 0))
        assertEquals("1 of 1 logged day on target", ProgressFormat.nutritionLine(1, 1))
        assertEquals("3 of 5 logged days on target", ProgressFormat.nutritionLine(5, 3))
    }

    @Test
    fun `achievements describe themselves`() {
        assertEquals("10 workouts finished", ProgressFormat.achievementDetail(Achievement(AchievementKind.WORKOUT_COUNT, 10, "10 workouts", null)))
        assertEquals("7 days in a row", ProgressFormat.achievementDetail(Achievement(AchievementKind.STREAK, 7, "7 day streak", null)))
        assertEquals("5 kg toward your goal", ProgressFormat.achievementDetail(Achievement(AchievementKind.WEIGHT_CHANGE, 5, "5 kg", null)))
    }

    @Test
    fun `a chart is described for screen readers`() {
        assertEquals("Weight chart, no data yet", ProgressFormat.seriesDescription("Weight", emptyList(), "kg"))
        assertEquals("Weight chart, 3 points, from 80.5 kg to 79 kg", ProgressFormat.seriesDescription("Weight", listOf(80.5, 80.0, 79.0), "kg"))
    }
}
