package com.example.fitapp.feature.lockin

import com.example.fitapp.feature.lockin.domain.LockinXpPredictor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LockinXpPredictorTest {
    @Test
    fun aNormalWorkoutPaysWorkoutAndSetXp() {
        val p = LockinXpPredictor.predict(countedSets = 5, prExercises = 0, completed = true, lockin = false, maxCombo = 0)
        assertEquals(60, p.total)
        assertEquals(listOf("Workout complete", "Sets completed"), p.lines.map { it.label })
        assertEquals(listOf(50, 10), p.lines.map { it.amount })
    }

    @Test
    fun setXpIsCappedAtFortyAndPrsPayTwentyFiveEach() {
        val p = LockinXpPredictor.predict(countedSets = 30, prExercises = 2, completed = true, lockin = false, maxCombo = 0)
        assertEquals(50 + 40 + 50, p.total)
    }

    @Test
    fun lockinAddsSeventyFivePercentFiftyAndTheCombo() {
        val p = LockinXpPredictor.predict(countedSets = 5, prExercises = 0, completed = true, lockin = true, maxCombo = 3)
        // session 60, bonus round(60 * 0.75) = 45, plus 50, plus a combo of 3
        assertEquals(60 + 45 + 50 + 3, p.total)
        assertEquals("Lock-in bonus", p.lines.last().label)
    }

    @Test
    fun theComboCountsForNoMoreThanTheSetsDone() {
        val p = LockinXpPredictor.predict(countedSets = 4, prExercises = 0, completed = true, lockin = true, maxCombo = 40)
        // session 58, bonus round(58 * 0.75) = 44 (43.5), plus 50, plus a combo capped at 4
        assertEquals(58 + 44 + 50 + 4, p.total)
    }

    @Test
    fun quittingEarnsSetXpOnly() {
        val p = LockinXpPredictor.predict(countedSets = 6, prExercises = 1, completed = false, lockin = true, maxCombo = 5)
        assertEquals(12 + 25, p.total)
        assertTrue(p.lines.none { it.label == "Workout complete" || it.label == "Lock-in bonus" })
    }

    @Test
    fun nothingDoneIsWorthNothing() {
        val p = LockinXpPredictor.predict(countedSets = 0, prExercises = 0, completed = false, lockin = true, maxCombo = 0)
        assertEquals(0, p.total)
        assertTrue(p.lines.isEmpty())
    }

    @Test
    fun theEstimateStopsAtTheDailyCap() {
        val p = LockinXpPredictor.predict(countedSets = 40, prExercises = 12, completed = true, lockin = true, maxCombo = 40)
        assertEquals(400, p.total)
    }
}
