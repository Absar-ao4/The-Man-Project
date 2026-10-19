package com.example.fitapp.feature.reminders

import com.example.fitapp.core.network.dto.BadgeCelebrationDto
import com.example.fitapp.core.network.dto.CelebrationsDto
import com.example.fitapp.core.network.dto.LevelChangeDto
import com.example.fitapp.core.network.dto.XpEventCelebrationDto
import com.example.fitapp.core.notifications.NotificationChannels
import com.example.fitapp.fakes.FakeAppForeground
import com.example.fitapp.fakes.FakeNotificationPreferences
import com.example.fitapp.fakes.FakeSocialRepository
import com.example.fitapp.fakes.MutableClock
import com.example.fitapp.fakes.RecordingPoster
import com.example.fitapp.fakes.duel
import com.example.fitapp.fakes.friendRequest
import com.example.fitapp.fakes.loaded
import com.example.fitapp.fakes.offlineWith
import com.example.fitapp.feature.gamification.domain.CelebrationCenter
import com.example.fitapp.feature.gamification.domain.CelebrationText
import com.example.fitapp.feature.gamification.domain.NotifyingCelebrationSink
import com.example.fitapp.feature.social.data.SocialInboxChecker
import com.example.fitapp.feature.social.domain.DuelStatus
import com.example.fitapp.feature.social.domain.InboxItem
import com.example.fitapp.feature.social.domain.Requests
import com.example.fitapp.feature.social.domain.SocialInbox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private val now = Instant.parse("2026-03-10T10:00:00Z") // the fake duels end at 2026-03-09T10:00:00Z

class SocialInboxTest {
    @Test
    fun `an incoming request becomes an item with the sender's name`() {
        val items = SocialInbox.items(listOf(friendRequest("r1", "a", "me")), emptyList(), now)

        assertEquals(1, items.size)
        assertEquals("request:r1", items[0].key)
        assertEquals("New friend request", items[0].title)
        assertEquals("Friend a (@user_a) wants to be your friend.", items[0].text)
    }

    @Test
    fun `only challenges made to you and still waiting are invitations`() {
        val duels =
            listOf(
                duel("d1", DuelStatus.PENDING, youAreChallenger = false),
                duel("d2", DuelStatus.PENDING, youAreChallenger = true),
                duel("d3", DuelStatus.ACTIVE, youAreChallenger = false),
            )

        val items = SocialInbox.items(emptyList(), duels, now)

        assertEquals(listOf("duel-invite:d1"), items.map { it.key })
        assertEquals("Rival d1 challenged you. Most workouts, over 7 days.", items.single().text)
    }

    @Test
    fun `a duel that just ended is a result you won, lost or tied`() {
        val duels =
            listOf(
                duel("w", DuelStatus.FINISHED, mine = 5, theirs = 3, winnerId = "me"),
                duel("l", DuelStatus.FINISHED, mine = 2, theirs = 4, winnerId = "ol"),
                duel("t", DuelStatus.FINISHED, mine = 3, theirs = 3, winnerId = null),
            )

        val items = SocialInbox.items(emptyList(), duels, now).associateBy { it.key }

        assertEquals("You won the duel", items.getValue("duel-result:w").title)
        assertEquals("Rival w: you 5, them 3.", items.getValue("duel-result:w").text)
        assertEquals("You lost the duel", items.getValue("duel-result:l").title)
        assertEquals("The duel ended in a tie", items.getValue("duel-result:t").title)
    }

    @Test
    fun `old results and results without an end time are history`() {
        val duels = listOf(duel("old", DuelStatus.FINISHED, winnerId = "me"), duel("active", DuelStatus.ACTIVE))
        val later = Instant.parse("2026-03-13T10:00:01Z") // more than 3 days after the end

        assertTrue(SocialInbox.items(emptyList(), duels, later).isEmpty())
        val before = Instant.parse("2026-03-08T10:00:00Z") // before it ended
        assertTrue(SocialInbox.items(emptyList(), duels, before).isEmpty())
        assertEquals(1, SocialInbox.items(emptyList(), duels, Instant.parse("2026-03-12T10:00:00Z")).size)
    }

    @Test
    fun `up to three items get a notification each, more get one summary`() {
        fun item(n: Int) = InboxItem("request:$n", InboxItem.Kind.REQUEST, "New friend request", "text $n")

        val three = SocialInbox.notifications((1..3).map(::item))
        val four = SocialInbox.notifications((1..4).map(::item))

        assertEquals(3, three.size)
        assertEquals(3, three.map { it.id }.toSet().size)
        assertTrue(three.all { it.channel == NotificationChannels.SOCIAL && it.route == "social" })
        assertEquals(1, four.size)
        assertEquals("4 new updates", four.single().title)
        assertTrue(SocialInbox.notifications(emptyList()).isEmpty())
    }

    @Test
    fun `an item always gets the same notification id`() {
        assertEquals(SocialInbox.idFor("request:1"), SocialInbox.idFor("request:1"))
        assertNotEquals(SocialInbox.idFor("request:1"), SocialInbox.idFor("request:2"))
    }
}

class SocialInboxCheckerTest {
    private val social = FakeSocialRepository()
    private val prefs = FakeNotificationPreferences()
    private val poster = RecordingPoster()
    private val foreground = FakeAppForeground(false)
    private val clock = MutableClock(now.toEpochMilli())
    private val checker = SocialInboxChecker(social, prefs, poster, foreground, clock)

    private fun incoming(vararg ids: String) {
        social.requests = loaded(Requests(ids.map { friendRequest(it, "from$it", "me") }, emptyList()))
    }

    @Test
    fun `a new request is announced once and not again`() =
        runTest {
            incoming("r1")

            assertEquals(1, checker.check())
            assertEquals(1, poster.posted.size)
            assertEquals(0, checker.check())
            assertEquals(1, poster.posted.size)
            assertEquals(setOf("request:r1"), prefs.seen)
        }

    @Test
    fun `only what is new is announced`() =
        runTest {
            incoming("r1")
            checker.check()
            incoming("r1", "r2")

            assertEquals(1, checker.check())
            assertEquals("Friend fromr2 (@user_fromr2) wants to be your friend.", poster.posted.last().text)
        }

    @Test
    fun `duel invitations and results are announced`() =
        runTest {
            social.duels = loaded(listOf(duel("d1", DuelStatus.PENDING, youAreChallenger = false), duel("d2", DuelStatus.FINISHED, mine = 4, theirs = 1, winnerId = "me")))

            assertEquals(2, checker.check())
            assertEquals(setOf("Duel challenge", "You won the duel"), poster.posted.map { it.title }.toSet())
        }

    @Test
    fun `nothing is shown while the app is on screen but it is not announced later either`() =
        runTest {
            incoming("r1")
            foreground.foreground = true

            assertEquals(0, checker.check())
            assertTrue(poster.posted.isEmpty())

            foreground.foreground = false
            assertEquals(0, checker.check())
            assertTrue(poster.posted.isEmpty())
        }

    @Test
    fun `switched off in the settings means nothing is shown and nothing is remembered`() =
        runTest {
            prefs.setSocialEnabled(false)
            incoming("r1")

            assertEquals(0, checker.check())
            assertTrue(poster.posted.isEmpty())
            assertTrue(prefs.seen.isEmpty())

            prefs.setSocialEnabled(true)
            assertEquals(1, checker.check())
        }

    @Test
    fun `a failed read with no saved copy changes nothing`() =
        runTest {
            incoming("r1")
            checker.check()
            social.requests = offlineWith(null)

            assertEquals(0, checker.check())
            assertEquals("the seen list is not wiped by a failed read", setOf("request:r1"), prefs.seen)
        }

    @Test
    fun `the seen list only keeps what is still waiting`() =
        runTest {
            incoming("r1", "r2")
            checker.check()
            incoming("r2")

            checker.check()

            assertEquals(setOf("request:r2"), prefs.seen)
        }

    @Test
    fun `without notification permission nothing is posted`() =
        runTest {
            poster.allowed = false
            incoming("r1")

            assertEquals(0, checker.check())
        }

    @Test
    fun `many at once become one summary`() =
        runTest {
            incoming("a", "b", "c", "d", "e")

            assertEquals(1, checker.check())
            assertEquals("5 new updates", poster.posted.single().title)
        }
}

class CelebrationTextTest {
    private fun xp(
        reason: String,
        amount: Int,
    ) = XpEventCelebrationDto(reason, amount)

    @Test
    fun `xp alone`() {
        val (title, text) = CelebrationText.summary(CelebrationsDto(xpEvents = listOf(xp("WORKOUT", 50), xp("SETS", 30))))!!

        assertEquals("XP earned", title)
        assertEquals("+80 XP", text)
    }

    @Test
    fun `a personal record is named`() {
        val (title, text) = CelebrationText.summary(CelebrationsDto(xpEvents = listOf(xp("PR", 25), xp("WORKOUT", 50))))!!

        assertEquals("New personal record", title)
        assertEquals("+75 XP. New personal record", text)
    }

    @Test
    fun `a level up leads and badges follow`() {
        val dto =
            CelebrationsDto(
                xpEvents = listOf(xp("WORKOUT", 50)),
                badges = listOf(BadgeCelebrationDto("first_workout", "First Workout")),
                levelChange = LevelChangeDto(2, 3, "Regular"),
            )

        val (title, text) = CelebrationText.summary(dto)!!

        assertEquals("Level 3 reached", title)
        assertEquals("+50 XP. Level up to 3, Regular. Badge unlocked: First Workout", text)
    }

    @Test
    fun `badges are counted in the title`() {
        val one = CelebrationText.summary(CelebrationsDto(badges = listOf(BadgeCelebrationDto("a", "A"))))!!
        val two = CelebrationText.summary(CelebrationsDto(badges = listOf(BadgeCelebrationDto("a", "A"), BadgeCelebrationDto("b", "B"))))!!

        assertEquals("Badge unlocked", one.first)
        assertEquals("2 badges unlocked", two.first)
    }

    @Test
    fun `nothing earned means nothing to say`() {
        assertNull(CelebrationText.summary(CelebrationsDto()))
        assertNull(CelebrationText.summary(CelebrationsDto(xpEvents = listOf(xp("WORKOUT", 0)))))
    }
}

class NotifyingCelebrationSinkTest {
    private val center = CelebrationCenter()
    private val poster = RecordingPoster()
    private val foreground = FakeAppForeground(false)
    private val prefs = FakeNotificationPreferences()
    // Unconfined, so what the sink launches has run by the time offer returns.
    private val sink = NotifyingCelebrationSink(center, poster, foreground, prefs, CoroutineScope(UnconfinedTestDispatcher()))

    private val dto = CelebrationsDto(xpEvents = listOf(XpEventCelebrationDto("WORKOUT", 50)))

    @Test
    fun `in the background the celebration is queued and also a notification`() =
        runTest {
            sink.offer(dto)

            assertEquals(1, center.queue.value.size)
            val n = poster.posted.single()
            assertEquals(NotificationChannels.CELEBRATIONS, n.channel)
            assertEquals("XP earned", n.title)
            assertEquals("home", n.route)
        }

    @Test
    fun `on screen only the in app celebration shows`() =
        runTest {
            foreground.foreground = true

            sink.offer(dto)

            assertEquals(1, center.queue.value.size)
            assertTrue(poster.posted.isEmpty())
        }

    @Test
    fun `switched off in the settings there is no notification`() =
        runTest {
            prefs.setCelebrationsEnabled(false)

            sink.offer(dto)

            assertEquals(1, center.queue.value.size)
            assertTrue(poster.posted.isEmpty())
        }

    @Test
    fun `an empty result posts nothing and queues nothing`() =
        runTest {
            sink.offer(CelebrationsDto())

            assertTrue(center.queue.value.isEmpty())
            assertFalse(poster.posted.isNotEmpty())
        }
}
