package com.example.fitapp.feature.reminders

import app.cash.turbine.test
import com.example.fitapp.MainDispatcherRule
import com.example.fitapp.core.notifications.NotificationChannels
import com.example.fitapp.fakes.FakeNotificationAccess
import com.example.fitapp.fakes.FakeNotificationPreferences
import com.example.fitapp.fakes.FakeReminderRepository
import com.example.fitapp.fakes.FakeReminderScheduler
import com.example.fitapp.fakes.FakeTodaySnapshots
import com.example.fitapp.fakes.RecordingPoster
import com.example.fitapp.fakes.snapshot
import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.reminders.data.ReminderFiring
import com.example.fitapp.feature.reminders.domain.Reminder
import com.example.fitapp.feature.reminders.domain.ReminderType
import com.example.fitapp.feature.reminders.ui.RemindersViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime

class RemindersViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repo = FakeReminderRepository()
    private val prefs = FakeNotificationPreferences()
    private val access = FakeNotificationAccess()
    private val scheduler = FakeReminderScheduler()

    private fun vm() = RemindersViewModel(repo, prefs, access, scheduler)

    @Test
    fun `every type is listed, off, until the user sets one up`() =
        runTest {
            val vm = vm()

            vm.state.test {
                val s = expectMostRecentItem()
                assertEquals(ReminderType.entries, s.reminders.map { it.type })
                assertTrue(s.reminders.none { it.enabled })
                assertTrue(s.reminders.all { it.isDefault })
                assertFalse(s.anyEnabled)
            }
        }

    @Test
    fun `switching a reminder on saves it as it is`() =
        runTest {
            val vm = vm()
            vm.state.test {
                expectMostRecentItem()

                vm.onToggle(ReminderType.WORKOUT, true)

                val s = expectMostRecentItem()
                assertTrue(s.reminders.first { it.type == ReminderType.WORKOUT }.enabled)
                assertEquals(ReminderType.WORKOUT.defaultTime, repo.saved.single().time)
                assertFalse(repo.saved.single().isDefault)
                assertTrue(s.anyEnabled)
                assertFalse("permission is already granted", s.askForPermission)
            }
        }

    @Test
    fun `switching one on without notification permission asks for it`() =
        runTest {
            access.notifications = false
            val vm = vm()
            vm.state.test {
                expectMostRecentItem()

                vm.onToggle(ReminderType.WATER, true)

                assertTrue(expectMostRecentItem().askForPermission)

                access.notifications = true
                vm.onAccessChecked()

                val after = expectMostRecentItem()
                assertFalse(after.askForPermission)
                assertTrue(after.notificationsAllowed)
            }
        }

    @Test
    fun `denied permission shows up as notifications being off`() =
        runTest {
            access.notifications = false
            val vm = vm()
            vm.state.test {
                assertFalse(expectMostRecentItem().notificationsAllowed)
            }
        }

    @Test
    fun `exact alarms being held back is reported`() =
        runTest {
            scheduler.exact = false
            val vm = vm()
            vm.state.test {
                assertFalse(expectMostRecentItem().exactAlarmsAllowed)

                scheduler.exact = true
                vm.onAccessChecked()

                assertTrue(expectMostRecentItem().exactAlarmsAllowed)
            }
        }

    @Test
    fun `editing opens a draft and a second tap closes it`() =
        runTest {
            val vm = vm()
            vm.state.test {
                expectMostRecentItem()

                vm.onEdit(ReminderType.MEAL_LUNCH)
                val open = expectMostRecentItem()
                assertEquals(ReminderType.MEAL_LUNCH, open.editing)
                assertEquals(ReminderType.MEAL_LUNCH.defaultTime, open.draft!!.time)

                vm.onEdit(ReminderType.MEAL_LUNCH)
                val closed = expectMostRecentItem()
                assertNull(closed.editing)
                assertNull(closed.draft)
            }
        }

    @Test
    fun `saving a draft of an untouched reminder switches it on with the new choices`() =
        runTest {
            val vm = vm()
            vm.state.test {
                expectMostRecentItem()
                vm.onEdit(ReminderType.WORKOUT)
                vm.onDraftTime(LocalTime.of(6, 45))
                vm.onDraftDay(2) // adds Tuesday to Mon, Wed, Fri
                vm.onDraftDay(1) // takes Monday away
                vm.onDraftMessage("Up and at it")

                vm.onSave()

                val s = expectMostRecentItem()
                val saved = repo.saved.single()
                assertTrue(saved.enabled)
                assertEquals(LocalTime.of(6, 45), saved.time)
                assertEquals(setOf(2, 3, 5), saved.days)
                assertEquals("Up and at it", saved.message)
                assertNull(s.editing)
                assertEquals("Saved", s.message)
            }
        }

    @Test
    fun `saving an edit keeps a reminder that was switched off, off`() =
        runTest {
            repo.set(Reminder("x", ReminderType.WORKOUT, false, LocalTime.of(18, 0), setOf(1), null, null))
            val vm = vm()
            vm.state.test {
                expectMostRecentItem()
                vm.onEdit(ReminderType.WORKOUT)
                vm.onDraftTime(LocalTime.of(19, 0))

                vm.onSave()
                expectMostRecentItem()

                assertFalse(repo.saved.single().enabled)
            }
        }

    @Test
    fun `a bad draft stays open with the reason and saves nothing`() =
        runTest {
            val vm = vm()
            vm.state.test {
                expectMostRecentItem()
                vm.onEdit(ReminderType.WORKOUT)
                vm.onDraftDay(1) // Monday, Wednesday and Friday are on by default: turn them all off
                vm.onDraftDay(3)
                vm.onDraftDay(5)
                vm.onSave()

                val s = expectMostRecentItem()
                assertEquals("Pick at least one day.", s.error)
                assertEquals(ReminderType.WORKOUT, s.editing)
                assertTrue(repo.saved.isEmpty())
            }
        }

    @Test
    fun `the water interval is read from what is typed`() =
        runTest {
            val vm = vm()
            vm.state.test {
                expectMostRecentItem()
                vm.onEdit(ReminderType.WATER)

                vm.onDraftInterval("45")
                assertEquals(45, expectMostRecentItem().draft!!.intervalMinutes)

                vm.onDraftInterval("abc")
                assertNull(expectMostRecentItem().draft!!.intervalMinutes)
            }
        }

    @Test
    fun `an over long message is cut one past the limit so the error can show`() =
        runTest {
            val vm = vm()
            vm.state.test {
                expectMostRecentItem()
                vm.onEdit(ReminderType.WORKOUT)

                vm.onDraftMessage("x".repeat(500))
                vm.onSave()

                val s = expectMostRecentItem()
                assertEquals(Reminder.MAX_MESSAGE + 1, s.draft!!.message!!.length)
                assertEquals("Keep the message to 140 characters.", s.error)
            }
        }

    @Test
    fun `reset goes back to the default and says so`() =
        runTest {
            repo.set(Reminder("x", ReminderType.STREAK, true, LocalTime.of(22, 0), setOf(1), null, null))
            val vm = vm()
            vm.state.test {
                expectMostRecentItem()

                vm.onReset(ReminderType.STREAK)

                val s = expectMostRecentItem()
                assertEquals(listOf(ReminderType.STREAK), repo.resets)
                assertTrue(s.reminders.first { it.type == ReminderType.STREAK }.isDefault)
                assertEquals("Back to the default", s.message)

                vm.onMessageShown()
                assertNull(expectMostRecentItem().message)
            }
        }

    @Test
    fun `the other notification switches are saved and ask for permission when needed`() =
        runTest {
            access.notifications = false
            val vm = vm()
            vm.state.test {
                assertTrue(expectMostRecentItem().socialNotifications)

                vm.onSocialNotifications(false)
                vm.onCelebrationNotifications(false)
                val off = expectMostRecentItem()
                assertFalse(off.socialNotifications)
                assertFalse(off.celebrationNotifications)
                assertFalse("switching off never asks", off.askForPermission)

                vm.onSocialNotifications(true)
                val on = expectMostRecentItem()
                assertTrue(on.socialNotifications)
                assertTrue(on.askForPermission)
            }
        }
}

class ReminderFiringTest {
    private val repo = FakeReminderRepository()
    private val snapshots = FakeTodaySnapshots()
    private val poster = RecordingPoster()
    private val scheduler = FakeReminderScheduler()
    private val firing = ReminderFiring(repo, snapshots, poster, scheduler)

    private fun on(
        type: ReminderType,
        message: String? = null,
    ) = repo.set(Reminder("id-$type", type, true, type.defaultTime, type.defaultDays, if (type.repeats) 120 else null, message))

    @Test
    fun `a due reminder is shown on the reminders channel and opens its screen`() =
        runTest {
            on(ReminderType.WORKOUT)

            firing.fire(ReminderType.WORKOUT)

            val n = poster.posted.single()
            assertEquals(ReminderType.WORKOUT.notificationId, n.id)
            assertEquals(NotificationChannels.REMINDERS, n.channel)
            assertEquals("Workout time", n.title)
            assertEquals("workout", n.route)
        }

    @Test
    fun `the next alarm is set whether or not it was shown`() =
        runTest {
            on(ReminderType.WORKOUT)
            snapshots.snapshot = snapshot(workouts = 1)

            firing.fire(ReminderType.WORKOUT)

            assertTrue("already trained, so nothing is shown", poster.posted.isEmpty())
            assertEquals(listOf(ReminderType.WORKOUT), scheduler.next.map { it.type })
        }

    @Test
    fun `a reminder that was switched off meanwhile is not shown`() =
        runTest {
            repo.set(Reminder("id", ReminderType.WATER, false, ReminderType.WATER.defaultTime, ReminderType.WATER.defaultDays, 120, null))

            firing.fire(ReminderType.WATER)

            assertTrue(poster.posted.isEmpty())
        }

    @Test
    fun `without a signed in user nothing is shown`() =
        runTest {
            on(ReminderType.WEIGH_IN)
            snapshots.snapshot = null

            firing.fire(ReminderType.WEIGH_IN)

            assertTrue(poster.posted.isEmpty())
            assertEquals(1, scheduler.next.size)
        }

    @Test
    fun `the custom message and today's numbers are used`() =
        runTest {
            on(ReminderType.WATER, message = "Hydrate")
            snapshots.snapshot = snapshot(water = 2)

            firing.fire(ReminderType.WATER)

            assertEquals("Hydrate", poster.posted.single().text)

            poster.posted.clear()
            on(ReminderType.WATER)
            firing.fire(ReminderType.WATER)
            assertEquals("You are at 2 of 8 glasses today.", poster.posted.single().text)
        }

    @Test
    fun `a meal reminder is skipped once that meal is logged`() =
        runTest {
            on(ReminderType.MEAL_BREAKFAST)
            snapshots.snapshot = snapshot(meals = setOf(MealType.BREAKFAST))

            firing.fire(ReminderType.MEAL_BREAKFAST)

            assertTrue(poster.posted.isEmpty())
        }

    @Test
    fun `without notification permission nothing is posted but the schedule goes on`() =
        runTest {
            poster.allowed = false
            on(ReminderType.WORKOUT)

            firing.fire(ReminderType.WORKOUT)

            assertTrue(poster.posted.isEmpty())
            assertNotNull(scheduler.next.singleOrNull())
        }
}
