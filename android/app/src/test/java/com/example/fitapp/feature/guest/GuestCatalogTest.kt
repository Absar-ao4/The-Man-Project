package com.example.fitapp.feature.guest

import com.example.fitapp.feature.guest.domain.ExampleMeter
import com.example.fitapp.feature.guest.domain.GuestCatalog
import com.example.fitapp.feature.guest.domain.GuestSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestCatalogTest {
    @Test
    fun `every tab has a headline an intro and features`() {
        GuestSection.entries.forEach { section ->
            val content = GuestCatalog.content(section)
            assertTrue(section.name, content.headline.isNotBlank())
            assertTrue(section.name, content.intro.isNotBlank())
            assertTrue(section.name, content.features.isNotEmpty())
        }
    }

    @Test
    fun `feature titles are unique inside a tab so each card has its own test tag`() {
        GuestSection.entries.forEach { section ->
            val titles = GuestCatalog.content(section).features.map { it.title }
            assertEquals(section.name, titles.size, titles.toSet().size)
        }
    }

    @Test
    fun `every feature explains itself and the lock message names it`() {
        GuestSection.entries.flatMap { GuestCatalog.content(it).features }.forEach { feature ->
            assertTrue(feature.title, feature.description.isNotBlank())
            assertTrue(feature.title, GuestCatalog.lockedMessage(feature).contains(feature.title))
        }
    }

    @Test
    fun `the example day is made of sensible meters`() {
        assertTrue(GuestCatalog.exampleDay.isNotEmpty())
        GuestCatalog.exampleDay.forEach { meter ->
            assertTrue(meter.label, meter.fraction in 0f..1f)
        }
    }

    @Test
    fun `a meter never goes past full and survives a missing target`() {
        assertEquals(1f, ExampleMeter("Water", 12, 8, "glasses").fraction, 0f)
        assertEquals(0f, ExampleMeter("Water", 3, 0, "glasses").fraction, 0f)
        assertEquals("6 / 8 glasses", ExampleMeter("Water", 6, 8, "glasses").text)
    }

    @Test
    fun `guest text has no em dash`() {
        val dash = 0x2014.toChar()
        GuestSection.entries.forEach { section ->
            val content = GuestCatalog.content(section)
            assertFalse(content.intro.contains(dash))
            content.features.forEach {
                assertFalse(it.description.contains(dash))
                assertFalse(GuestCatalog.lockedMessage(it).contains(dash))
            }
        }
    }
}