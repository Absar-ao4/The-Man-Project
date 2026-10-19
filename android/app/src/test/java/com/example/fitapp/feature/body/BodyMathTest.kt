package com.example.fitapp.feature.body

import com.example.fitapp.feature.body.domain.BmiCategory
import com.example.fitapp.feature.body.domain.BodyMath
import com.example.fitapp.feature.body.domain.RatioCategory
import com.example.fitapp.feature.body.domain.WeightEntry
import com.example.fitapp.feature.profile.domain.Goal
import com.example.fitapp.feature.profile.domain.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The same cases as the backend's BodyMathTest: the phone has to show the same numbers the server would. */
class BodyMathTest {
    private val start = LocalDate.of(2026, 3, 1)

    private fun at(
        day: Int,
        kg: Double,
    ) = WeightEntry("w$day", start.plusDays(day.toLong()), kg)

    private fun daily(
        days: Int,
        from: Double,
        to: Double,
    ) = (0 until days).map { at(it, from + (to - from) * it / maxOf(1, days - 1)) }

    // ------------------------------------------------------------------ trend

    @Test
    fun `the trend is the mean of the last seven days including gaps`() {
        val trend = BodyMath.trend(listOf(at(0, 80.0), at(1, 82.0), at(3, 78.0), at(8, 81.0)))

        assertEquals(80.0, trend[0].trendKg, 0.0)
        assertEquals(81.0, trend[1].trendKg, 0.0)
        assertEquals(80.0, trend[2].trendKg, 0.0)
        assertEquals(79.5, trend[3].trendKg, 0.0)
        assertEquals(81.0, trend[3].weightKg, 0.0)
    }

    @Test
    fun `entries in any order give the same trend`() {
        val ordered = listOf(at(0, 80.0), at(1, 82.0), at(2, 78.0))
        val shuffled = listOf(at(2, 78.0), at(0, 80.0), at(1, 82.0))

        assertEquals(BodyMath.trend(ordered), BodyMath.trend(shuffled))
        assertTrue(BodyMath.trend(emptyList()).isEmpty())
    }

    @Test
    fun `one heavy day moves the trend only a little`() {
        val week = daily(6, 80.0, 80.0) + at(6, 83.0)

        assertEquals(80.4, BodyMath.trend(week)[6].trendKg, 0.05)
    }

    @Test
    fun `the trend as of a date carries over a short break`() {
        val entries = listOf(at(0, 80.0), at(1, 82.0), at(2, 78.0))

        assertEquals(80.0, BodyMath.trendAsOf(entries, start.plusDays(2))!!, 0.0)
        assertEquals(80.0, BodyMath.trendAsOf(entries, start.plusDays(5))!!, 0.0)
        assertEquals(80.0, BodyMath.trendAsOf(entries, start.plusDays(40))!!, 0.0)
        assertNull(BodyMath.trendAsOf(entries, start.minusDays(1)))
        assertNull(BodyMath.trendAsOf(emptyList(), start))
    }

    // ------------------------------------------------------------------ BMI and ratios

    @Test
    fun `bmi uses height in metres squared`() {
        assertEquals(22.9, BodyMath.bmi(70.0, 175.0)!!, 0.0)
        assertEquals(30.9, BodyMath.bmi(100.0, 180.0)!!, 0.0)
        assertNull(BodyMath.bmi(70.0, null))
        assertNull(BodyMath.bmi(70.0, 50.0))
        assertNull(BodyMath.bmi(0.0, 175.0))
    }

    @Test
    fun `bmi categories follow the who cut offs`() {
        assertEquals(BmiCategory.UNDERWEIGHT, BodyMath.bmiCategory(18.4))
        assertEquals(BmiCategory.NORMAL, BodyMath.bmiCategory(18.5))
        assertEquals(BmiCategory.NORMAL, BodyMath.bmiCategory(24.9))
        assertEquals(BmiCategory.OVERWEIGHT, BodyMath.bmiCategory(25.0))
        assertEquals(BmiCategory.OVERWEIGHT, BodyMath.bmiCategory(29.9))
        assertEquals(BmiCategory.OBESE, BodyMath.bmiCategory(30.0))
    }

    @Test
    fun `waist to height and its categories`() {
        assertEquals(0.5, BodyMath.waistToHeight(85.0, 170.0)!!, 0.0)
        assertNull(BodyMath.waistToHeight(null, 170.0))
        assertNull(BodyMath.waistToHeight(85.0, null))
        assertEquals(RatioCategory.LOW, BodyMath.ratioCategory(0.39))
        assertEquals(RatioCategory.HEALTHY, BodyMath.ratioCategory(0.40))
        assertEquals(RatioCategory.HEALTHY, BodyMath.ratioCategory(0.49))
        assertEquals(RatioCategory.INCREASED, BodyMath.ratioCategory(0.50))
        assertEquals(RatioCategory.INCREASED, BodyMath.ratioCategory(0.59))
        assertEquals(RatioCategory.HIGH, BodyMath.ratioCategory(0.60))
    }

    // ------------------------------------------------------------------ US Navy body fat

    @Test
    fun `the navy formula for men`() {
        val bf = BodyMath.navyBodyFat(Sex.MALE, 180.0, 85.0, 38.0, null)!!

        assertTrue(bf in 14.0..17.0)
        assertTrue(BodyMath.navyBodyFat(Sex.MALE, 180.0, 95.0, 38.0, null)!! > bf)
        assertTrue(BodyMath.navyBodyFat(Sex.MALE, 180.0, 85.0, 42.0, null)!! < bf)
    }

    @Test
    fun `the navy formula for women needs hips`() {
        val bf = BodyMath.navyBodyFat(Sex.FEMALE, 165.0, 75.0, 33.0, 95.0)!!

        assertTrue(bf in 24.0..31.0)
        assertNull(BodyMath.navyBodyFat(Sex.FEMALE, 165.0, 75.0, 33.0, null))
    }

    @Test
    fun `body fat is not shown for bad inputs`() {
        assertNull(BodyMath.navyBodyFat(Sex.OTHER, 180.0, 85.0, 38.0, 95.0))
        assertNull(BodyMath.navyBodyFat(null, 180.0, 85.0, 38.0, 95.0))
        assertNull(BodyMath.navyBodyFat(Sex.MALE, 180.0, 35.0, 38.0, null))
        assertNull(BodyMath.navyBodyFat(Sex.MALE, null, 85.0, 38.0, null))
        assertNull(BodyMath.navyBodyFat(Sex.MALE, 180.0, null, 38.0, null))
        assertNull(BodyMath.navyBodyFat(Sex.MALE, 180.0, 38.5, 38.0, null))
    }

    // ------------------------------------------------------------------ goals

    @Test
    fun `fat loss is reached at five percent below the start`() {
        assertFalse(BodyMath.goalReached(Goal.FAT_LOSS, daily(30, 90.0, 86.0)))
        assertTrue(BodyMath.goalReached(Goal.FAT_LOSS, daily(30, 90.0, 83.0)))
        assertFalse(BodyMath.goalReached(Goal.MUSCLE_GAIN, daily(30, 90.0, 83.0)))
    }

    @Test
    fun `muscle gain is reached at three percent above the start`() {
        assertFalse(BodyMath.goalReached(Goal.MUSCLE_GAIN, daily(30, 70.0, 71.0)))
        assertTrue(BodyMath.goalReached(Goal.MUSCLE_GAIN, daily(30, 70.0, 74.0)))
    }

    @Test
    fun `a goal needs at least two weeks of data`() {
        assertFalse(BodyMath.goalReached(Goal.FAT_LOSS, daily(10, 90.0, 70.0)))
        assertFalse(BodyMath.goalReached(Goal.FAT_LOSS, listOf(at(0, 90.0))))
    }

    @Test
    fun `maintenance means staying within two percent for four weeks`() {
        val steady = (0 until 35).map { at(it, 75.0 + if (it % 2 == 0) 0.4 else -0.4) }

        assertTrue(BodyMath.goalReached(Goal.MAINTENANCE, steady))
        assertFalse(BodyMath.goalReached(Goal.MAINTENANCE, steady.subList(0, 20)))
        assertFalse(BodyMath.goalReached(Goal.MAINTENANCE, daily(35, 75.0, 80.0)))
        assertNotNull(BodyMath.trend(steady).firstOrNull())
    }
}
