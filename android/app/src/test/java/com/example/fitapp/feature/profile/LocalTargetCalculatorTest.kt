package com.example.fitapp.feature.profile

import com.example.fitapp.feature.profile.domain.ActivityLevel
import com.example.fitapp.feature.profile.domain.Goal
import com.example.fitapp.feature.profile.domain.LocalTargetCalculator
import com.example.fitapp.feature.profile.domain.Sex
import com.example.fitapp.feature.profile.domain.TargetSuggestionInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors the backend TargetCalculatorTest so the offline fallback matches the server. */
class LocalTargetCalculatorTest {
    private fun input(
        age: Int = 30,
        sex: Sex = Sex.MALE,
        height: Double = 180.0,
        weight: Double = 80.0,
        activity: ActivityLevel = ActivityLevel.MODERATE,
        goal: Goal = Goal.MAINTENANCE,
    ) = TargetSuggestionInput(age, sex, height, weight, activity, goal)

    @Test
    fun `muscle gain male matches the server's exact numbers`() {
        val r = LocalTargetCalculator.suggest(input(goal = Goal.MUSCLE_GAIN))
        assertEquals(1780, r.bmr)
        assertEquals(2759, r.tdee)
        assertEquals(3104, r.calories)
        assertEquals(144, r.proteinG)
        assertEquals(86, r.fatG)
        assertEquals(439, r.carbsG)
        assertEquals(12, r.waterGlasses)
    }

    @Test
    fun `maintenance equals tdee`() {
        val r = LocalTargetCalculator.suggest(input())
        assertEquals(r.tdee, r.calories)
    }

    @Test
    fun `fat loss sits 15 to 20 percent under maintenance`() {
        val r =
            LocalTargetCalculator.suggest(
                input(age = 25, sex = Sex.FEMALE, height = 165.0, weight = 60.0, activity = ActivityLevel.LIGHT, goal = Goal.FAT_LOSS),
            )
        val ratio = r.calories.toDouble() / r.tdee
        assertTrue("ratio was $ratio", ratio in 0.80..0.85)
    }

    @Test
    fun `calorie floor protects against extreme deficits`() {
        val r =
            LocalTargetCalculator.suggest(
                input(age = 60, sex = Sex.FEMALE, height = 150.0, weight = 40.0, activity = ActivityLevel.SEDENTARY, goal = Goal.FAT_LOSS),
            )
        assertTrue(r.calories >= 1200)
    }

    @Test
    fun `protein stays inside 1_6 to 2_2 g per kg`() {
        Goal.entries.forEach { goal ->
            val r = LocalTargetCalculator.suggest(input(weight = 100.0, goal = goal))
            assertTrue(r.proteinG / 100.0 in 1.6..2.2)
        }
    }

    @Test
    fun `water is clamped`() {
        assertEquals(6, LocalTargetCalculator.suggest(input(weight = 30.0)).waterGlasses)
        assertEquals(16, LocalTargetCalculator.suggest(input(weight = 200.0)).waterGlasses)
    }
}
