package com.example.fitapp.feature.workout

import com.example.fitapp.feature.workout.domain.PrDetector
import com.example.fitapp.feature.workout.domain.PrDetector.SetPerformance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Mirrors the backend PrDetectorTest: the two implementations must agree. */
class PrDetectorTest {
    private val t0 = Instant.parse("2026-01-01T10:00:00Z")
    private var order = 0

    private fun set(
        id: String,
        kg: Double,
        reps: Int,
        day: Long,
        exercise: String = "bench",
    ) = SetPerformance(id, exercise, kg, reps, t0.plusSeconds(day * 86_400), order++)

    @Test
    fun `epley matches the formula`() {
        assertEquals(116.6667, PrDetector.epley(100.0, 5), 0.001)
        assertEquals(103.3333, PrDetector.epley(100.0, 1), 0.001)
        assertEquals(80.0, PrDetector.epley(60.0, 10), 0.001)
    }

    @Test
    fun `first ever set is a baseline not a pr`() {
        val a = PrDetector.analyze(listOf(set("a", 80.0, 8, 0)))
        assertTrue(a.prSetIds.isEmpty())
        assertEquals("a", a.bests.getValue("bench").e1rm.setId)
    }

    @Test
    fun `higher e1rm is a pr`() {
        val a = PrDetector.analyze(listOf(set("a", 80.0, 8, 0), set("b", 85.0, 8, 7)))
        assertEquals(setOf("b"), a.prSetIds)
    }

    @Test
    fun `heavier weight at the same rep count is a pr even with lower e1rm`() {
        val a =
            PrDetector.analyze(
                listOf(
                    set("fives", 100.0, 5, 0),
                    set("triple1", 90.0, 3, 1),
                    set("triple2", 92.5, 3, 7),
                ),
            )
        assertEquals(setOf("triple2"), a.prSetIds)
        assertEquals("fives", a.bests.getValue("bench").e1rm.setId)
    }

    @Test
    fun `a new rep count only sets a baseline`() {
        val a = PrDetector.analyze(listOf(set("fives", 100.0, 5, 0), set("triple", 60.0, 3, 7)))
        assertTrue(a.prSetIds.isEmpty())
    }

    @Test
    fun `equal or lower performance is not a pr`() {
        val a = PrDetector.analyze(listOf(set("a", 100.0, 5, 0), set("b", 100.0, 5, 7), set("c", 95.0, 5, 14)))
        assertTrue(a.prSetIds.isEmpty())
    }

    @Test
    fun `input order does not matter only time does`() {
        val early = set("early", 80.0, 8, 0)
        val late = set("late", 90.0, 8, 10)
        assertEquals(setOf("late"), PrDetector.analyze(listOf(late, early)).prSetIds)
    }

    @Test
    fun `exercises are independent`() {
        val a =
            PrDetector.analyze(
                listOf(
                    set("b1", 80.0, 8, 0),
                    set("s1", 100.0, 8, 0, "squat"),
                    set("b2", 85.0, 8, 7),
                    set("s2", 100.0, 8, 7, "squat"),
                ),
            )
        assertEquals(setOf("b2"), a.prSetIds)
        assertEquals(setOf("bench", "squat"), a.bests.keys)
    }

    @Test
    fun `unweighted and out of range sets are ignored`() {
        val a = PrDetector.analyze(listOf(set("bw", 0.0, 20, 0), set("zero", 100.0, 0, 1), set("many", 20.0, 31, 2)))
        assertTrue(a.prSetIds.isEmpty())
        assertTrue(a.bests.isEmpty())
    }

    @Test
    fun `tiny improvements are ignored`() {
        val a = PrDetector.analyze(listOf(set("a", 100.0, 5, 0), set("b", 100.001, 5, 7)))
        assertTrue(a.prSetIds.isEmpty())
    }
}
