package com.example.fitapp.feature.workout

import com.example.fitapp.feature.workout.ui.formatClock
import com.example.fitapp.feature.workout.ui.formatDuration
import com.example.fitapp.feature.workout.ui.formatVolume
import com.example.fitapp.feature.workout.ui.formatWeight
import com.example.fitapp.feature.workout.ui.parseWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration

class FormattersTest {
    @Test
    fun `whole weights drop the decimal`() {
        assertEquals("80", formatWeight(80.0))
        assertEquals("82.5", formatWeight(82.5))
        assertEquals("0", formatWeight(0.0))
    }

    @Test
    fun `volume is grouped kilograms`() {
        assertEquals("12,480 kg", formatVolume(12480.0))
        assertEquals("950 kg", formatVolume(950.0))
    }

    @Test
    fun `durations read naturally`() {
        assertEquals("In progress", formatDuration(null))
        assertEquals("45m", formatDuration(Duration.ofMinutes(45)))
        assertEquals("1h 05m", formatDuration(Duration.ofMinutes(65)))
        assertEquals("0m", formatDuration(Duration.ofSeconds(20)))
    }

    @Test
    fun `clock shows minutes and padded seconds`() {
        assertEquals("1:15", formatClock(75))
        assertEquals("0:05", formatClock(5))
        assertEquals("0:00", formatClock(-3))
        assertEquals("10:00", formatClock(600))
    }

    @Test
    fun `weights accept commas and reject junk`() {
        assertEquals(82.5, parseWeight("82,5")!!, 0.0)
        assertEquals(80.0, parseWeight(" 80 ")!!, 0.0)
        assertNull(parseWeight(""))
        assertNull(parseWeight("abc"))
        assertNull(parseWeight("-5"))
    }
}
