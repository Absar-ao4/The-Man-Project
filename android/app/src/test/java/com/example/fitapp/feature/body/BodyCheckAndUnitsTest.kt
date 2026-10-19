package com.example.fitapp.feature.body

import com.example.fitapp.fakes.weight
import com.example.fitapp.feature.body.domain.BmiCategory
import com.example.fitapp.feature.body.domain.BodyCheckCalculator
import com.example.fitapp.feature.body.domain.BodyInput
import com.example.fitapp.feature.body.domain.BodyMath
import com.example.fitapp.feature.body.domain.InputResult
import com.example.fitapp.feature.body.domain.Measurement
import com.example.fitapp.feature.body.domain.MeasurementType
import com.example.fitapp.feature.body.domain.RatioCategory
import com.example.fitapp.feature.body.domain.UnitFormat
import com.example.fitapp.feature.profile.domain.ActivityLevel
import com.example.fitapp.feature.profile.domain.FitnessLevel
import com.example.fitapp.feature.profile.domain.Goal
import com.example.fitapp.feature.profile.domain.Sex
import com.example.fitapp.feature.profile.domain.Units
import com.example.fitapp.feature.profile.domain.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private val today = LocalDate.of(2026, 10, 17)

private fun profile(
    sex: Sex? = Sex.MALE,
    heightCm: Double? = 180.0,
) = UserProfile(
    id = "p",
    name = "Sam",
    age = 30,
    sex = sex,
    heightCm = heightCm,
    weightKg = 80.0,
    fitnessLevel = FitnessLevel.INTERMEDIATE,
    activityLevel = ActivityLevel.MODERATE,
    goal = Goal.MAINTENANCE,
    timezone = "UTC",
    currency = "USD",
    units = Units.METRIC,
)

private fun measure(
    type: MeasurementType,
    cm: Double,
    daysAgo: Long = 1,
) = Measurement("m-$type-$daysAgo", today.minusDays(daysAgo), type, cm)

class BodyCheckCalculatorTest {
    private val weights = (0..6).map { weight(today.minusDays(it.toLong()), 80.0) }

    @Test
    fun `with nothing logged it says what to do first`() {
        val check = BodyCheckCalculator.compute(emptyList(), emptyList(), profile(), today)

        assertNull(check.latest)
        assertNull(check.bmi)
        assertTrue(check.notes.any { it.startsWith("Log your weight") })
    }

    @Test
    fun `bmi comes from the trend and carries the caveat`() {
        val check = BodyCheckCalculator.compute(weights, emptyList(), profile(), today)

        assertEquals(80.0, check.trendKg!!, 0.0)
        assertEquals(24.7, check.bmi!!, 0.0)
        assertEquals(BmiCategory.NORMAL, check.bmiCategory)
        assertTrue(check.notes.contains(BodyMath.BMI_NOTE))
    }

    @Test
    fun `without a height it asks for one and shows no bmi`() {
        val check = BodyCheckCalculator.compute(weights, emptyList(), profile(heightCm = null), today)

        assertNull(check.bmi)
        assertTrue(check.notes.any { it.contains("height") })
        assertTrue(!check.notes.contains(BodyMath.BMI_NOTE))
    }

    @Test
    fun `the week change compares the trend with a week earlier`() {
        val moving = (0..14).map { weight(today.minusDays(it.toLong()), 80.0 + it * 0.1) } // heavier the further back
        val check = BodyCheckCalculator.compute(moving, emptyList(), profile(), today)

        assertNotNull(check.weekChangeKg)
        assertTrue(check.weekChangeKg!! < 0)
    }

    @Test
    fun `waist gives the ratio and a prompt is shown without one`() {
        val with = BodyCheckCalculator.compute(weights, listOf(measure(MeasurementType.WAIST, 90.0)), profile(), today)
        val without = BodyCheckCalculator.compute(weights, emptyList(), profile(), today)

        assertEquals(0.5, with.waistToHeight!!, 0.0)
        assertEquals(RatioCategory.INCREASED, with.ratioCategory)
        assertNull(without.waistToHeight)
        assertTrue(without.notes.any { it.contains("waist") })
    }

    @Test
    fun `body fat needs sex, waist and neck, and hips for women`() {
        val tape = listOf(measure(MeasurementType.WAIST, 85.0), measure(MeasurementType.NECK, 38.0))

        val male = BodyCheckCalculator.compute(weights, tape, profile(Sex.MALE), today)
        val female = BodyCheckCalculator.compute(weights, tape, profile(Sex.FEMALE, 165.0), today)
        val other = BodyCheckCalculator.compute(weights, tape, profile(Sex.OTHER), today)

        assertNotNull(male.bodyFatPercent)
        assertNull(female.bodyFatPercent)
        assertTrue(female.notes.any { it.contains("hip") })
        assertNull(other.bodyFatPercent)
        assertTrue(other.notes.any { it.contains("male or female") })
    }

    @Test
    fun `measurements older than four months are not used`() {
        val old = listOf(measure(MeasurementType.WAIST, 90.0, daysAgo = 121))
        val recent = listOf(measure(MeasurementType.WAIST, 90.0, daysAgo = 120))

        assertNull(BodyCheckCalculator.compute(weights, old, profile(), today).waistToHeight)
        assertNotNull(BodyCheckCalculator.compute(weights, recent, profile(), today).waistToHeight)
    }

    @Test
    fun `the newest measurement of a kind wins`() {
        val tape = listOf(measure(MeasurementType.WAIST, 100.0, daysAgo = 30), measure(MeasurementType.WAIST, 90.0, daysAgo = 2))

        assertEquals(0.5, BodyCheckCalculator.compute(weights, tape, profile(), today).waistToHeight!!, 0.0)
    }

    @Test
    fun `trend points cover the asked window and use earlier weigh-ins for the trend`() {
        val points = BodyCheckCalculator.trendPoints(weights + weight(today.minusDays(40), 90.0), today, 7)

        assertEquals(7, points.size)
        assertTrue(points.none { it.date.isBefore(today.minusDays(7)) })
    }
}

class UnitFormatTest {
    @Test
    fun `kilograms and pounds convert both ways`() {
        assertEquals(176.4, UnitFormat.kgToLb(80.0), 0.05)
        assertEquals(80.0, UnitFormat.lbToKg(UnitFormat.kgToLb(80.0)), 1e-9)
        assertEquals(1.0, UnitFormat.cmToIn(2.54), 1e-9)
        assertEquals(2.54, UnitFormat.inToCm(1.0), 1e-9)
    }

    @Test
    fun `weights and lengths are shown in the chosen units`() {
        assertEquals("80 kg", UnitFormat.weight(80.0, imperial = false))
        assertEquals("80.4 kg", UnitFormat.weight(80.4, imperial = false))
        assertEquals("176.4 lb", UnitFormat.weight(80.0, imperial = true))
        assertEquals("86.5 cm", UnitFormat.length(86.5, imperial = false))
        assertEquals("34 in", UnitFormat.length(86.4, imperial = true))
    }

    @Test
    fun `a change shows its sign and a tiny one shows as zero`() {
        assertEquals("+0.4 kg", UnitFormat.signedWeight(0.4, imperial = false))
        assertEquals("-1.2 kg", UnitFormat.signedWeight(-1.2, imperial = false))
        assertEquals("0 kg", UnitFormat.signedWeight(0.04, imperial = false))
        assertEquals("+2.2 lb", UnitFormat.signedWeight(1.0, imperial = true))
    }

    @Test
    fun `typed numbers are read with commas and converted to kilograms`() {
        assertEquals(80.5, UnitFormat.parseWeightKg("80,5", imperial = false)!!, 0.0)
        assertEquals(80.0, UnitFormat.parseWeightKg(" 176.4 ", imperial = true)!!, 0.0)
        assertNull(UnitFormat.parseWeightKg("abc", imperial = false))
        assertNull(UnitFormat.parseWeightKg("0", imperial = false))
        assertNull(UnitFormat.parseWeightKg("-5", imperial = false))
        assertNull(UnitFormat.parseWeightKg("", imperial = false))
        assertEquals(86.4, UnitFormat.parseLengthCm("34", imperial = true)!!, 0.0)
        assertEquals(90.0, UnitFormat.parseLengthCm("90", imperial = false)!!, 0.0)
    }
}

class BodyInputTest {
    @Test
    fun `a good weight is returned in kilograms`() {
        assertEquals(InputResult.Ok(80.0), BodyInput.weight("80", Units.METRIC))
        assertEquals(InputResult.Ok(80.0), BodyInput.weight("176.4", Units.IMPERIAL))
    }

    @Test
    fun `the limits are twenty and five hundred kilograms inclusive`() {
        assertEquals(InputResult.Ok(20.0), BodyInput.weight("20", Units.METRIC))
        assertEquals(InputResult.Ok(500.0), BodyInput.weight("500", Units.METRIC))
        assertTrue(BodyInput.weight("19.9", Units.METRIC) is InputResult.Invalid)
        assertTrue(BodyInput.weight("500.1", Units.METRIC) is InputResult.Invalid)
    }

    @Test
    fun `messages speak the units the person uses`() {
        val metric = BodyInput.weight("5", Units.METRIC) as InputResult.Invalid
        val imperial = BodyInput.weight("5", Units.IMPERIAL) as InputResult.Invalid

        assertTrue(metric.message.contains("kg"))
        assertTrue(imperial.message.contains("lb"))
        assertTrue((BodyInput.weight("abc", Units.METRIC) as InputResult.Invalid).message.contains("number"))
    }

    @Test
    fun `measurements are limited to five to three hundred centimetres`() {
        assertEquals(InputResult.Ok(85.0), BodyInput.length("85", Units.METRIC))
        assertTrue(BodyInput.length("4", Units.METRIC) is InputResult.Invalid)
        assertTrue(BodyInput.length("301", Units.METRIC) is InputResult.Invalid)
        assertTrue((BodyInput.length("1", Units.IMPERIAL) as InputResult.Invalid).message.contains("in"))
    }
}
