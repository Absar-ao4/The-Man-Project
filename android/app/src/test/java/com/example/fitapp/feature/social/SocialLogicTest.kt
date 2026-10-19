package com.example.fitapp.feature.social

import com.example.fitapp.fakes.card
import com.example.fitapp.fakes.duel
import com.example.fitapp.fakes.feedItem
import com.example.fitapp.feature.social.domain.DuelStatus
import com.example.fitapp.feature.social.domain.DuelText
import com.example.fitapp.feature.social.domain.FeedText
import com.example.fitapp.feature.social.domain.FeedType
import com.example.fitapp.feature.social.domain.InviteLink
import com.example.fitapp.feature.social.domain.IsoWeeks
import com.example.fitapp.feature.social.domain.LeaderboardMetric
import com.example.fitapp.feature.social.domain.LeaderboardText
import com.example.fitapp.feature.social.ui.QrEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class IsoWeeksTest {
    @Test
    fun `a date becomes its iso week and back to its monday`() {
        assertEquals("2026-W11", IsoWeeks.of(LocalDate.of(2026, 3, 9)))
        assertEquals("2026-W11", IsoWeeks.of(LocalDate.of(2026, 3, 15)))
        assertEquals(LocalDate.of(2026, 3, 9), IsoWeeks.monday("2026-W11"))
        // The first days of January can belong to the last week of the old year, and late December to week 1.
        assertEquals("2026-W01", IsoWeeks.of(LocalDate.of(2025, 12, 29)))
        assertEquals("2026-W53", IsoWeeks.of(LocalDate.of(2027, 1, 3)))
        assertEquals(LocalDate.of(2025, 12, 29), IsoWeeks.monday("2026-W01"))
    }

    @Test
    fun `invalid labels have no monday`() {
        assertNull(IsoWeeks.monday("nonsense"))
        assertNull(IsoWeeks.monday("2026-W00"))
        assertNull(IsoWeeks.monday("2026-W54"))
        assertNull(IsoWeeks.monday("2025-W53")) // 2025 has 52 weeks
    }

    @Test
    fun `weeks step back and forward across a year end`() {
        assertEquals("2026-W10", IsoWeeks.previous("2026-W11"))
        assertEquals("2026-W12", IsoWeeks.next("2026-W11"))
        assertEquals("2025-W52", IsoWeeks.previous("2026-W01"))
        assertEquals("2026-W01", IsoWeeks.next("2025-W52"))
        assertEquals("2026-W53", IsoWeeks.previous("2027-W01"))
        assertNull(IsoWeeks.previous("junk"))
        assertNull(IsoWeeks.next("junk"))
    }

    @Test
    fun `titles say this week, last week or the week number`() {
        val today = LocalDate.of(2026, 3, 11)
        assertEquals("This week", IsoWeeks.title("2026-W11", today))
        assertEquals("Last week", IsoWeeks.title("2026-W10", today))
        assertEquals("Week 8, 2026", IsoWeeks.title("2026-W08", today))
        assertEquals("junk", IsoWeeks.title("junk", today))
    }
}

class InviteLinkTest {
    @Test
    fun `a code becomes a link and a link or a bare code gives the code back`() {
        assertEquals("manproject://friend/ABCD2345", InviteLink.toLink("abcd2345"))
        assertEquals("ABCD2345", InviteLink.parse("manproject://friend/ABCD2345"))
        assertEquals("ABCD2345", InviteLink.parse("  MANPROJECT://friend/abcd2345  "))
        assertEquals("ABCD2345", InviteLink.parse("abcd2345"))
    }

    @Test
    fun `text that is not a code is refused`() {
        assertNull(InviteLink.parse(""))
        assertNull(InviteLink.parse("abc"))
        assertNull(InviteLink.parse("manproject://friend/"))
        assertNull(InviteLink.parse("https://evil.example/ABCD2345"))
        assertNull(InviteLink.parse("ABCD 2345"))
        assertNull(InviteLink.parse("ABCD2345ABCD2345"))
    }

    @Test
    fun `the share text carries both the code and the link`() {
        val text = InviteLink.shareText("abcd2345")
        assertTrue(text.contains("ABCD2345"))
        assertTrue(text.contains("manproject://friend/ABCD2345"))
    }
}

class FeedTextTest {
    @Test
    fun `each kind of event reads as a sentence`() {
        assertEquals("Friend f finished Push day (55 min, 12 sets)", FeedText.describe(feedItem("1")))
        assertEquals(
            "Friend f completed a Lock-in session (30 min)",
            FeedText.describe(feedItem("2", FeedType.LOCKIN_COMPLETED, mapOf("minutes" to "30"))),
        )
        assertEquals("Friend f earned the badge Personal Best", FeedText.describe(feedItem("3", FeedType.BADGE, mapOf("name" to "Personal Best"))))
        assertEquals("Friend f reached level 5 (Regular)", FeedText.describe(feedItem("4", FeedType.LEVEL_UP, mapOf("level" to "5", "title" to "Regular"))))
        assertEquals("Friend f won a workouts duel", FeedText.describe(feedItem("5", FeedType.DUEL_WON, mapOf("metric" to "WORKOUTS"))))
        assertEquals("Friend f did something new", FeedText.describe(feedItem("6", FeedType.UNKNOWN, emptyMap())))
    }

    @Test
    fun `a pr shows its weight only when the server sent it`() {
        val hidden = feedItem("1", FeedType.PR, mapOf("exercise" to "Bench Press"))
        val shown = feedItem("2", FeedType.PR, mapOf("exercise" to "Bench Press", "weightKg" to "102.5", "reps" to "5"))
        val whole = feedItem("3", FeedType.PR, mapOf("exercise" to "Squat", "weightKg" to "140.0", "reps" to "3"))

        assertEquals("Friend f set a PR on Bench Press", FeedText.describe(hidden))
        assertEquals("Friend f set a PR on Bench Press: 102.5 kg x 5", FeedText.describe(shown))
        assertEquals("Friend f set a PR on Squat: 140 kg x 3", FeedText.describe(whole))
    }

    @Test
    fun `missing details fall back to plain words`() {
        assertEquals("Friend f finished a workout", FeedText.describe(feedItem("1", payload = emptyMap())))
        assertEquals("Friend f set a PR on an exercise", FeedText.describe(feedItem("2", FeedType.PR, emptyMap())))
        assertEquals("Friend f finished Legs (1 set)", FeedText.describe(feedItem("3", payload = mapOf("name" to "Legs", "sets" to "1", "minutes" to "0"))))
    }
}

class DuelTextTest {
    @Test
    fun `status is told from the viewers side`() {
        assertEquals("Waiting for Rival a", DuelText.status(duel("a", DuelStatus.PENDING, youAreChallenger = true)))
        assertEquals("Rival b challenged you", DuelText.status(duel("b", DuelStatus.PENDING, youAreChallenger = false)))
        assertEquals("You are ahead", DuelText.status(duel("c", mine = 3, theirs = 1)))
        assertEquals("You are behind", DuelText.status(duel("d", mine = 1, theirs = 3)))
        assertEquals("All square", DuelText.status(duel("e", mine = 2, theirs = 2)))
        assertEquals("You won", DuelText.status(duel("f", DuelStatus.FINISHED, mine = 3, theirs = 1, winnerId = "me")))
        assertEquals("Rival g won", DuelText.status(duel("g", DuelStatus.FINISHED, mine = 1, theirs = 3, winnerId = "og")))
        assertEquals("A draw", DuelText.status(duel("h", DuelStatus.FINISHED, mine = 2, theirs = 2)))
        assertEquals("Declined", DuelText.status(duel("i", DuelStatus.DECLINED)))
        assertEquals("Cancelled", DuelText.status(duel("j", DuelStatus.CANCELLED)))
        assertEquals("Expired", DuelText.status(duel("k", DuelStatus.EXPIRED)))
    }

    @Test
    fun `my side and their side follow who challenged`() {
        val mine = duel("a", youAreChallenger = true, mine = 4, theirs = 1)
        val theirs = duel("b", youAreChallenger = false, mine = 4, theirs = 1)

        for (d in listOf(mine, theirs)) {
            assertEquals("Me", d.me.name)
            assertEquals(4, d.myScore)
            assertEquals(1, d.theirScore)
        }
        assertFalse(mine.needsMyAnswer)
        assertTrue(duel("p", DuelStatus.PENDING, youAreChallenger = true).canWithdraw)
    }

    @Test
    fun `only a challenge for me can be answered`() {
        val waiting = duel("a", DuelStatus.PENDING, youAreChallenger = false)
        assertTrue(waiting.needsMyAnswer)
        assertFalse(waiting.canWithdraw)
        assertFalse(duel("b", DuelStatus.ACTIVE, youAreChallenger = false).needsMyAnswer)
    }
}

class LeaderboardTextTest {
    @Test
    fun `values are shown with their unit`() {
        assertEquals("1,250 XP", LeaderboardText.value(LeaderboardMetric.XP, 1250.0))
        assertEquals("1 workout", LeaderboardText.value(LeaderboardMetric.WORKOUTS, 1.0))
        assertEquals("3 workouts", LeaderboardText.value(LeaderboardMetric.WORKOUTS, 3.0))
        assertEquals("1 day", LeaderboardText.value(LeaderboardMetric.STREAK, 1.0))
        assertEquals("12 days", LeaderboardText.value(LeaderboardMetric.STREAK, 12.0))
        assertEquals("75%", LeaderboardText.value(LeaderboardMetric.CONSISTENCY, 75.0))
        assertEquals("66.7%", LeaderboardText.value(LeaderboardMetric.CONSISTENCY, 66.66))
        assertEquals("+7.5%", LeaderboardText.value(LeaderboardMetric.IMPROVEMENT, 7.5))
        assertEquals("-3%", LeaderboardText.value(LeaderboardMetric.IMPROVEMENT, -3.0))
        assertEquals("-2.5%", LeaderboardText.value(LeaderboardMetric.IMPROVEMENT, -2.5))
        assertEquals("0%", LeaderboardText.value(LeaderboardMetric.IMPROVEMENT, 0.0))
    }

    @Test
    fun `no value reads as no data`() {
        assertEquals("No data yet", LeaderboardText.value(LeaderboardMetric.XP, null))
    }
}

class QrEncoderTest {
    @Test
    fun `a code makes a square grid with the three finder corners`() {
        val grid = QrEncoder.matrix(InviteLink.toLink("ABCD2345"))

        val n = grid.size
        assertTrue(n >= 21)
        assertTrue(grid.all { it.size == n })
        // Every QR code has a dark 7x7 finder pattern ring in three corners.
        for (corner in listOf(0 to 0, 0 to n - 7, n - 7 to 0)) {
            val (row, col) = corner
            assertTrue(grid[row][col])
            assertTrue(grid[row][col + 6])
            assertTrue(grid[row + 6][col])
            assertTrue(grid[row + 6][col + 6])
            assertFalse(grid[row + 1][col + 1])
            assertTrue(grid[row + 3][col + 3])
        }
    }

    @Test
    fun `the same text gives the same code and different text a different one`() {
        assertEquals(QrEncoder.matrix("manproject://friend/ABCD2345"), QrEncoder.matrix("manproject://friend/ABCD2345"))
        assertFalse(QrEncoder.matrix("manproject://friend/ABCD2345") == QrEncoder.matrix("manproject://friend/ZZZZ2345"))
    }

    @Test
    fun `nothing to encode is refused`() {
        val failed = runCatching { QrEncoder.matrix("") }
        assertTrue(failed.isFailure)
        assertNotNull(card("x"))
    }
}
