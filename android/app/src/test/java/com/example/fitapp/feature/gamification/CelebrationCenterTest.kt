package com.example.fitapp.feature.gamification

import com.example.fitapp.core.network.dto.BadgeCelebrationDto
import com.example.fitapp.core.network.dto.CelebrationsDto
import com.example.fitapp.core.network.dto.LevelChangeDto
import com.example.fitapp.core.network.dto.XpEventCelebrationDto
import com.example.fitapp.feature.gamification.domain.Celebration
import com.example.fitapp.feature.gamification.domain.CelebrationCenter
import com.example.fitapp.feature.gamification.domain.xpReasonLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CelebrationCenterTest {
    private val center = CelebrationCenter()

    @Test
    fun anEmptyReplyQueuesNothing() {
        center.offer(CelebrationsDto())
        assertTrue(center.queue.value.isEmpty())
    }

    @Test
    fun xpIsMergedIntoOneCelebrationWithOneLinePerReason() {
        center.offer(
            CelebrationsDto(
                xpEvents =
                    listOf(
                        XpEventCelebrationDto("WORKOUT", 50),
                        XpEventCelebrationDto("SETS", 6),
                        XpEventCelebrationDto("PR", 25),
                        XpEventCelebrationDto("PR", 25),
                    ),
            ),
        )

        val xp = center.queue.value.single() as Celebration.XpGained
        assertEquals(106, xp.total)
        assertEquals(listOf("Workout complete", "Sets completed", "New personal record"), xp.lines.map { it.label })
        assertEquals(listOf(50, 6, 50), xp.lines.map { it.amount })
    }

    @Test
    fun zeroXpEventsAreNotCelebrated() {
        center.offer(CelebrationsDto(xpEvents = listOf(XpEventCelebrationDto("WORKOUT", 0))))
        assertTrue(center.queue.value.isEmpty())
    }

    @Test
    fun xpThenLevelUpThenBadgesInThatOrder() {
        center.offer(
            CelebrationsDto(
                xpEvents = listOf(XpEventCelebrationDto("WORKOUT", 50)),
                badges =
                    listOf(
                        BadgeCelebrationDto("first_workout", "First Rep", "Finish a workout."),
                        BadgeCelebrationDto("first_pr", "Personal Best", "Set a record."),
                    ),
                levelChange = LevelChangeDto(1, 2, "Rookie"),
            ),
        )

        val queue = center.queue.value
        assertEquals(4, queue.size)
        assertTrue(queue[0] is Celebration.XpGained)
        assertEquals(2, (queue[1] as Celebration.LevelUp).to)
        assertEquals("First Rep", (queue[2] as Celebration.BadgeUnlocked).name)
        assertEquals("Personal Best", (queue[3] as Celebration.BadgeUnlocked).name)
    }

    @Test
    fun everyCelebrationGetsItsOwnId() {
        center.offer(CelebrationsDto(xpEvents = listOf(XpEventCelebrationDto("WORKOUT", 50))))
        center.offer(CelebrationsDto(xpEvents = listOf(XpEventCelebrationDto("WORKOUT", 53))))

        assertEquals(2, center.queue.value.map { it.id }.toSet().size)
    }

    @Test
    fun dismissingRemovesOnlyThatOne() {
        center.offer(CelebrationsDto(levelChange = LevelChangeDto(1, 2, "Rookie"), badges = listOf(BadgeCelebrationDto("a", "A"))))
        val first = center.queue.value.first()

        center.dismiss(first.id)

        assertEquals(1, center.queue.value.size)
        assertTrue(center.queue.value.single() is Celebration.BadgeUnlocked)
    }

    @Test
    fun dismissingAnUnknownIdChangesNothing() {
        center.offer(CelebrationsDto(levelChange = LevelChangeDto(1, 2, "Rookie")))
        center.dismiss(9_999)
        assertEquals(1, center.queue.value.size)
    }

    @Test
    fun unknownReasonsStillGetAReadableLabel() {
        assertEquals("XP earned", xpReasonLabel("SOMETHING_NEW"))
        assertEquals("Lock-in bonus", xpReasonLabel("LOCKIN_BONUS"))
    }
}
