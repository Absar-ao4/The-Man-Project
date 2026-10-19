package com.example.fitapp.feature.lockin

import com.example.fitapp.feature.lockin.domain.ComboTracker
import com.example.fitapp.feature.lockin.domain.ComboTracker.DoneSet
import org.junit.Assert.assertEquals
import org.junit.Test

class ComboTrackerTest {
    private val start = 1_000_000L

    private fun at(seconds: Int) = start + seconds * 1000L

    @Test
    fun noSetsNoCombo() {
        val result = ComboTracker.compute(start, emptyList())
        assertEquals(0, result.current)
        assertEquals(0, result.max)
    }

    @Test
    fun theFirstSetStartsTheComboIfItComesWithinFiveMinutes() {
        assertEquals(1, ComboTracker.compute(start, listOf(DoneSet(at(120), 60))).current)
        assertEquals(1, ComboTracker.compute(start, listOf(DoneSet(at(300), 60))).current)
    }

    @Test
    fun aVeryLateFirstSetStillCountsAsOne() {
        val result = ComboTracker.compute(start, listOf(DoneSet(at(900), 60)))
        assertEquals(1, result.current)
        assertEquals(1, result.max)
    }

    @Test
    fun setsInsideTheRestWindowBuildTheCombo() {
        // rest 60 s plus 90 s to do the next set: 150 s between completions is still in time
        val sets = listOf(DoneSet(at(60), 60), DoneSet(at(60 + 150), 60), DoneSet(at(60 + 300), 60))
        val result = ComboTracker.compute(start, sets)
        assertEquals(3, result.current)
        assertEquals(3, result.max)
    }

    @Test
    fun aSetOneSecondPastTheWindowBreaksTheCombo() {
        val sets = listOf(DoneSet(at(60), 60), DoneSet(at(60 + 151), 60))
        val result = ComboTracker.compute(start, sets)
        assertEquals(1, result.current)
    }

    @Test
    fun theBestComboIsKeptAfterItBreaks() {
        val sets =
            listOf(
                DoneSet(at(60), 60),
                DoneSet(at(120), 60),
                DoneSet(at(180), 60),
                DoneSet(at(180 + 1000), 60), // long break
                DoneSet(at(180 + 1060), 60),
            )
        val result = ComboTracker.compute(start, sets)
        assertEquals(2, result.current)
        assertEquals(3, result.max)
    }

    @Test
    fun theWindowFollowsTheRestOfThePreviousSet() {
        // a long 180 s rest gives 270 s to do the next set
        val within = listOf(DoneSet(at(60), 180), DoneSet(at(60 + 270), 60))
        assertEquals(2, ComboTracker.compute(start, within).current)
        val late = listOf(DoneSet(at(60), 180), DoneSet(at(60 + 271), 60))
        assertEquals(1, ComboTracker.compute(start, late).current)
    }

    @Test
    fun theOrderOfTheListDoesNotMatter() {
        val sets = listOf(DoneSet(at(180), 60), DoneSet(at(60), 60), DoneSet(at(120), 60))
        assertEquals(3, ComboTracker.compute(start, sets).max)
    }

    @Test
    fun undoingASetReplaysTheRest() {
        val all = listOf(DoneSet(at(60), 60), DoneSet(at(120), 60), DoneSet(at(180), 60))
        assertEquals(3, ComboTracker.compute(start, all).max)
        assertEquals(2, ComboTracker.compute(start, all.dropLast(1)).max)
    }

    @Test
    fun theLiveComboDropsToZeroOnceTheWindowRunsOut() {
        val last = DoneSet(at(100), 60)
        val result = ComboTracker.Result(current = 4, max = 4)

        assertEquals(4, ComboTracker.live(result, last, at(100)))
        assertEquals(4, ComboTracker.live(result, last, at(100 + 150)))
        assertEquals(0, ComboTracker.live(result, last, at(100 + 151)))
        assertEquals(0, ComboTracker.live(result, null, at(100)))
    }

    @Test
    fun theWindowFractionDrainsFromOneToZero() {
        val last = DoneSet(at(100), 60) // window of 150 s
        assertEquals(1f, ComboTracker.windowFraction(last, at(100)), 0.001f)
        assertEquals(0.5f, ComboTracker.windowFraction(last, at(175)), 0.001f)
        assertEquals(0f, ComboTracker.windowFraction(last, at(400)), 0.001f)
        assertEquals(0f, ComboTracker.windowFraction(null, at(100)), 0.001f)
    }

    @Test
    fun theMultiplierGoesFromOneToTwoAtTenInARow() {
        assertEquals(1.0, ComboTracker.multiplier(0), 0.0001)
        assertEquals(1.1, ComboTracker.multiplier(1), 0.0001)
        assertEquals(1.6, ComboTracker.multiplier(6), 0.0001)
        assertEquals(2.0, ComboTracker.multiplier(10), 0.0001)
        assertEquals(2.0, ComboTracker.multiplier(40), 0.0001)
        assertEquals(1.0, ComboTracker.multiplier(-3), 0.0001)
    }
}
