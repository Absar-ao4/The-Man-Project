package com.example.fitapp.feature.reminders

import com.example.fitapp.core.database.ReminderEntity
import com.example.fitapp.fakes.snapshot
import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.reminders.data.daysText
import com.example.fitapp.feature.reminders.data.parseDays
import com.example.fitapp.feature.reminders.data.parseReminderTime
import com.example.fitapp.feature.reminders.data.toDomain
import com.example.fitapp.feature.reminders.domain.Reminder
import com.example.fitapp.feature.reminders.domain.ReminderCopy
import com.example.fitapp.feature.reminders.domain.ReminderRules
import com.example.fitapp.feature.reminders.domain.ReminderTimes
import com.example.fitapp.feature.reminders.domain.ReminderType
import com.example.fitapp.feature.reminders.domain.ReminderValidation
import com.example.fitapp.feature.reminders.domain.daysSummary
import com.example.fitapp.feature.reminders.domain.timeText
import com.example.fitapp.navigation.MainTab
import com.example.fitapp.navigation.NutritionRoutes
import com.example.fitapp.navigation.ProgressRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

private val zone = ZoneId.of("Asia/Karachi")

/** 2026-03-09 is a Monday. */
private fun at(
    day: Int,
    hour: Int,
    minute: Int = 0,
    zoneId: ZoneId = zone,
) = ZonedDateTime.of(2026, 3, day, hour, minute, 0, 0, zoneId)

private fun reminder(
    type: ReminderType = ReminderType.WORKOUT,
    hour: Int = 18,
    minute: Int = 0,
    days: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    interval: Int? = null,
    enabled: Boolean = true,
    message: String? = null,
) = Reminder("r", type, enabled, LocalTime.of(hour, minute), days, interval, message)

class ReminderTimesTest {
    @Test
    fun `a reminder later today fires today`() {
        val next = ReminderTimes.next(reminder(hour = 18), at(9, 8))!!

        assertEquals(at(9, 18), next)
    }

    @Test
    fun `a reminder already past fires tomorrow`() {
        assertEquals(at(10, 18), ReminderTimes.next(reminder(hour = 18), at(9, 19))!!)
    }

    @Test
    fun `exactly at the time means the next one, never the same moment again`() {
        assertEquals(at(10, 18), ReminderTimes.next(reminder(hour = 18), at(9, 18))!!)
    }

    @Test
    fun `only the chosen days count`() {
        val monWedFri = reminder(days = setOf(1, 3, 5))

        assertEquals(at(11, 18), ReminderTimes.next(monWedFri, at(9, 19))!!) // Monday evening, so Wednesday
        assertEquals(at(13, 18), ReminderTimes.next(monWedFri, at(11, 19))!!) // Wednesday evening, so Friday
        assertEquals(at(16, 18), ReminderTimes.next(monWedFri, at(13, 19))!!) // Friday evening, so Monday
    }

    @Test
    fun `a week later is still found for a single day`() {
        val mondayOnly = reminder(days = setOf(1))

        assertEquals(at(16, 18), ReminderTimes.next(mondayOnly, at(9, 18))!!)
    }

    @Test
    fun `no days means no next time`() {
        assertNull(ReminderTimes.next(reminder(days = emptySet()), at(9, 8)))
    }

    @Test
    fun `just before midnight rolls to the next day`() {
        assertEquals(at(10, 7, 30), ReminderTimes.next(reminder(ReminderType.WEIGH_IN, 7, 30), at(9, 23, 59))!!)
    }

    @Test
    fun `a time that does not exist on the daylight saving day moves forward`() {
        val newYork = ZoneId.of("America/New_York") // clocks jump from 02:00 to 03:00 on 2026-03-08
        val next = ReminderTimes.next(reminder(hour = 2, minute = 30), ZonedDateTime.of(2026, 3, 8, 0, 0, 0, 0, newYork))!!

        assertEquals(LocalDate.of(2026, 3, 8), next.toLocalDate())
        assertEquals(3, next.hour)
    }

    @Test
    fun `water repeats through the day until nine in the evening`() {
        val water = reminder(ReminderType.WATER, 9, 0, interval = 120)

        assertEquals(listOf(9, 11, 13, 15, 17, 19, 21), ReminderTimes.timesOfDay(water).map { it.hour })
        assertEquals(at(9, 11), ReminderTimes.next(water, at(9, 9))!!)
        assertEquals(at(9, 15), ReminderTimes.next(water, at(9, 13, 30))!!)
        assertEquals(at(10, 9), ReminderTimes.next(water, at(9, 21))!!) // nothing after 21:00, so tomorrow morning
    }

    @Test
    fun `a water interval that does not divide the day still stops at nine`() {
        val times = ReminderTimes.timesOfDay(reminder(ReminderType.WATER, 9, 0, interval = 90))

        assertEquals(LocalTime.of(21, 0), times.last())
        assertEquals(9, times.size)
        val odd = ReminderTimes.timesOfDay(reminder(ReminderType.WATER, 9, 0, interval = 100))
        assertTrue(odd.last() <= LocalTime.of(21, 0))
    }

    @Test
    fun `water that starts late fires once`() {
        assertEquals(listOf(LocalTime.of(22, 0)), ReminderTimes.timesOfDay(reminder(ReminderType.WATER, 22, 0, interval = 60)))
        assertEquals(listOf(LocalTime.of(20, 30)), ReminderTimes.timesOfDay(reminder(ReminderType.WATER, 20, 30, interval = 60)))
    }

    @Test
    fun `the interval is ignored for reminders that do not repeat`() {
        assertEquals(listOf(LocalTime.of(18, 0)), ReminderTimes.timesOfDay(reminder(ReminderType.WORKOUT, 18, 0, interval = 30)))
    }
}

class ReminderRulesTest {
    @Test
    fun `a workout reminder is skipped once you trained`() {
        assertTrue(ReminderRules.shouldNotify(ReminderType.WORKOUT, snapshot()))
        assertFalse(ReminderRules.shouldNotify(ReminderType.WORKOUT, snapshot(workouts = 1)))
    }

    @Test
    fun `water stops at the goal and always asks without a goal`() {
        assertTrue(ReminderRules.shouldNotify(ReminderType.WATER, snapshot(water = 7)))
        assertFalse(ReminderRules.shouldNotify(ReminderType.WATER, snapshot(water = 8)))
        assertFalse(ReminderRules.shouldNotify(ReminderType.WATER, snapshot(water = 10)))
        assertTrue(ReminderRules.shouldNotify(ReminderType.WATER, snapshot(water = 20, waterTarget = null)))
    }

    @Test
    fun `each meal reminder only looks at its own meal`() {
        val breakfastOnly = snapshot(meals = setOf(MealType.BREAKFAST))

        assertFalse(ReminderRules.shouldNotify(ReminderType.MEAL_BREAKFAST, breakfastOnly))
        assertTrue(ReminderRules.shouldNotify(ReminderType.MEAL_LUNCH, breakfastOnly))
        assertTrue(ReminderRules.shouldNotify(ReminderType.MEAL_DINNER, breakfastOnly))
        assertFalse(ReminderRules.shouldNotify(ReminderType.MEAL_DINNER, snapshot(meals = setOf(MealType.DINNER))))
    }

    @Test
    fun `a snack does not stand in for a meal`() {
        assertTrue(ReminderRules.shouldNotify(ReminderType.MEAL_LUNCH, snapshot(meals = setOf(MealType.SNACK))))
    }

    @Test
    fun `the weigh-in reminder is skipped after weighing in`() {
        assertTrue(ReminderRules.shouldNotify(ReminderType.WEIGH_IN, snapshot()))
        assertFalse(ReminderRules.shouldNotify(ReminderType.WEIGH_IN, snapshot(weighed = true)))
    }

    @Test
    fun `the goal check is skipped when every goal is done`() {
        val done = snapshot(workouts = 1, water = 8, calories = 2000.0, protein = 100.0)

        assertEquals(1f, done.completion, 0f)
        assertFalse(ReminderRules.shouldNotify(ReminderType.DAILY_GOAL, done))
        assertTrue(ReminderRules.shouldNotify(ReminderType.DAILY_GOAL, snapshot(workouts = 1, water = 8, calories = 2000.0, protein = 60.0)))
    }

    @Test
    fun `the streak warning needs a streak and no workout yet`() {
        assertTrue(ReminderRules.shouldNotify(ReminderType.STREAK, snapshot(streak = 5)))
        assertFalse(ReminderRules.shouldNotify(ReminderType.STREAK, snapshot(streak = 0)))
        assertFalse(ReminderRules.shouldNotify(ReminderType.STREAK, snapshot(streak = 5, workouts = 1)))
    }
}

class ReminderCopyTest {
    @Test
    fun `standard wording uses today's numbers`() {
        val water = ReminderCopy.text(reminder(ReminderType.WATER, interval = 120), snapshot(water = 3))
        assertEquals("Time for some water", water.title)
        assertEquals("You are at 3 of 8 glasses today.", water.body)

        val noGoal = ReminderCopy.text(reminder(ReminderType.WATER, interval = 120), snapshot(water = 3, waterTarget = null))
        assertEquals("You have had 3 glasses today.", noGoal.body)

        assertEquals("Keep your 12 day streak alive", ReminderCopy.text(reminder(ReminderType.STREAK), snapshot(streak = 12)).title)
        assertEquals("Today's goals: 25% done", ReminderCopy.text(reminder(ReminderType.DAILY_GOAL), snapshot(workouts = 1)).title)
        assertEquals("Log your lunch", ReminderCopy.text(reminder(ReminderType.MEAL_LUNCH), snapshot()).title)
    }

    @Test
    fun `a custom message replaces the body but not the title`() {
        val text = ReminderCopy.text(reminder(ReminderType.WORKOUT, message = "  Leg day, no excuses  "), snapshot())

        assertEquals("Workout time", text.title)
        assertEquals("Leg day, no excuses", text.body)
    }

    @Test
    fun `a blank message is the standard wording`() {
        assertEquals("Your session is waiting. Open the app to start.", ReminderCopy.text(reminder(message = "   "), snapshot()).body)
    }
}

class ReminderModelTest {
    @Test
    fun `every type has a usable default`() {
        ReminderType.entries.forEach { type ->
            val default = Reminder.default(type)
            assertFalse("$type is off until chosen", default.enabled)
            assertTrue(default.isDefault)
            assertTrue("$type has days", default.days.isNotEmpty())
            assertNull(ReminderValidation.check(default))
            assertEquals(type.repeats, default.intervalMinutes != null)
        }
        assertTrue(ReminderType.WATER.repeats)
        assertEquals(1, ReminderType.entries.count { it.repeats })
    }

    @Test
    fun `notification ids are unique and routes match the navigation`() {
        assertEquals(ReminderType.entries.size, ReminderType.entries.map { it.notificationId }.toSet().size)
        assertEquals(MainTab.WORKOUT.route, ReminderType.WORKOUT.route)
        assertEquals(MainTab.WORKOUT.route, ReminderType.STREAK.route)
        assertEquals(MainTab.HOME.route, ReminderType.WATER.route)
        assertEquals(MainTab.HOME.route, ReminderType.DAILY_GOAL.route)
        assertEquals(NutritionRoutes.GRAPH, ReminderType.MEAL_LUNCH.route)
        assertEquals(ProgressRoutes.BODY, ReminderType.WEIGH_IN.route)
    }

    @Test
    fun `days and times read well`() {
        assertEquals("Every day", daysSummary((1..7).toSet()))
        assertEquals("Weekdays", daysSummary(setOf(1, 2, 3, 4, 5)))
        assertEquals("Weekends", daysSummary(setOf(6, 7)))
        assertEquals("Mon, Wed, Fri", daysSummary(setOf(5, 1, 3)))
        assertEquals("No days", daysSummary(emptySet()))
        assertEquals("07:05", timeText(LocalTime.of(7, 5)))
    }
}

class ReminderValidationTest {
    @Test
    fun `a good reminder passes`() {
        assertNull(ReminderValidation.check(reminder()))
        assertNull(ReminderValidation.check(reminder(ReminderType.WATER, interval = 45)))
        assertNull(ReminderValidation.check(reminder(message = "x".repeat(140))))
    }

    @Test
    fun `problems are named in plain words`() {
        assertEquals("Pick at least one day.", ReminderValidation.check(reminder(days = emptySet())))
        assertEquals("Days run from Monday to Sunday.", ReminderValidation.check(reminder(days = setOf(0, 1))))
        assertEquals("Repeat every 30 to 720 minutes.", ReminderValidation.check(reminder(ReminderType.WATER, interval = 10)))
        assertEquals("Repeat every 30 to 720 minutes.", ReminderValidation.check(reminder(ReminderType.WATER, interval = 721)))
        assertEquals("Only the water reminder repeats.", ReminderValidation.check(reminder(ReminderType.WORKOUT, interval = 60)))
        assertEquals("Keep the message to 140 characters.", ReminderValidation.check(reminder(message = "x".repeat(141))))
    }
}

class ReminderMappersTest {
    private fun entity(
        type: String = "WORKOUT",
        time: String = "18:30",
        days: String = "1,3,5",
        interval: Int? = null,
        message: String? = null,
    ) = ReminderEntity("id", "u", type, true, time, days, interval, message, 1L)

    @Test
    fun `a row becomes a reminder`() {
        val r = entity(message = "Lift").toDomain()!!

        assertEquals(ReminderType.WORKOUT, r.type)
        assertEquals(LocalTime.of(18, 30), r.time)
        assertEquals(setOf(1, 3, 5), r.days)
        assertEquals("Lift", r.message)
        assertTrue(r.enabled)
        assertFalse(r.isDefault)
    }

    @Test
    fun `an unknown type is skipped`() {
        assertNull(entity(type = "TELEPORT").toDomain())
    }

    @Test
    fun `a broken time or day list falls back to the defaults`() {
        val r = entity(type = "WEIGH_IN", time = "soon", days = "x,9,0").toDomain()!!

        assertEquals(ReminderType.WEIGH_IN.defaultTime, r.time)
        assertEquals(ReminderType.WEIGH_IN.defaultDays, r.days)
    }

    @Test
    fun `water gets its interval and others never do`() {
        assertEquals(Reminder.DEFAULT_WATER_INTERVAL, entity(type = "WATER").toDomain()!!.intervalMinutes)
        assertEquals(90, entity(type = "WATER", interval = 90).toDomain()!!.intervalMinutes)
        assertNull(entity(type = "WORKOUT", interval = 90).toDomain()!!.intervalMinutes)
    }

    @Test
    fun `a blank message is none`() {
        assertNull(entity(message = "   ").toDomain()!!.message)
    }

    @Test
    fun `times and days read and write the same way the server stores them`() {
        assertEquals(LocalTime.of(7, 30), parseReminderTime("07:30:00", LocalTime.NOON))
        assertEquals(LocalTime.NOON, parseReminderTime("nonsense", LocalTime.NOON))
        assertEquals(setOf(1, 7), parseDays("7, 1, 7"))
        assertEquals("1,3,5", daysText(setOf(5, 1, 3)))
        assertNotNull(parseDays(""))
    }
}
