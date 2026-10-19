package com.example.fitapp.feature

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.fitapp.core.animation.LocalReduceMotion
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.feature.gamification.domain.BadgeStatus
import com.example.fitapp.feature.gamification.domain.Celebration
import com.example.fitapp.feature.gamification.domain.ProgressStats
import com.example.fitapp.feature.gamification.domain.XpLine
import com.example.fitapp.feature.gamification.ui.CelebrationDialog
import com.example.fitapp.feature.gamification.ui.ProgressCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class GamificationUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val stats = ProgressStats(2_450, 7, "Grinder", 300, 1_000, 4, 9, 1_250_000.0, 2, 120, 400)

    private val badges =
        listOf(
            BadgeStatus("first_workout", "First Rep", "Finish your first workout.", "fitness", true, Instant.parse("2026-03-01T10:00:00Z")),
            BadgeStatus("streak_7", "Week Warrior", "Train 7 days in a row.", "flame", false, null),
        )

    @Test
    fun progressCardShowsLevelTitleAndToday() {
        compose.setContent { ManTheme { ProgressCard(stats, badges) } }

        compose.onNodeWithTag("progress_level").assertTextEquals("Level 7")
        compose.onNodeWithTag("progress_title").assertTextEquals("Grinder")
        compose.onNodeWithText("120 of 400 XP").assertIsDisplayed()
        compose.onNodeWithText("4 days (best 9)").assertIsDisplayed()
        compose.onNodeWithText("1.25 million kg").assertIsDisplayed()
    }

    @Test
    fun progressCardListsEarnedAndLockedBadges() {
        compose.setContent { ManTheme { ProgressCard(stats, badges) } }

        compose.onNodeWithText("Badges 1 of 2").assertIsDisplayed()
        compose.onNodeWithTag("badge_first_workout").assertIsDisplayed()
        compose.onNodeWithTag("badge_streak_7").assertIsDisplayed()
    }

    @Test
    fun progressCardWithoutStatsExplainsWhatToDo() {
        compose.setContent { ManTheme { ProgressCard(null, emptyList()) } }
        compose.onNodeWithTag("progress_empty").assertIsDisplayed()
    }

    @Test
    fun xpCelebrationShowsTheTotalAndEachReason() {
        compose.setContent {
            ManTheme {
                CelebrationDialog(
                    Celebration.XpGained(1, 56, listOf(XpLine("Workout complete", 50), XpLine("Sets completed", 6))),
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithTag("celebration_title").assertTextEquals("+56 XP")
        compose.onNodeWithText("Workout complete").assertIsDisplayed()
        compose.onNodeWithText("+6").assertIsDisplayed()
    }

    @Test
    fun levelUpCelebrationNamesTheNewLevelAndTitle() {
        compose.setContent { ManTheme { CelebrationDialog(Celebration.LevelUp(2, 4, 5, "Grinder"), onDismiss = {}) } }

        compose.onNodeWithTag("celebration_title").assertTextEquals("Level up!")
        compose.onNodeWithTag("celebration_level").assertTextEquals("Level 5, Grinder")
    }

    @Test
    fun badgeCelebrationShowsTheBadge() {
        compose.setContent {
            ManTheme { CelebrationDialog(Celebration.BadgeUnlocked(3, "first_pr", "Personal Best", "Set your first record."), onDismiss = {}) }
        }

        compose.onNodeWithTag("celebration_title").assertTextEquals("Badge unlocked")
        compose.onNodeWithTag("celebration_badge").assertTextEquals("Personal Best")
        compose.onNodeWithText("Set your first record.").assertIsDisplayed()
    }

    @Test
    fun dismissingCallsBack() {
        var dismissed = 0
        compose.setContent {
            ManTheme { CelebrationDialog(Celebration.LevelUp(4, 1, 2, "Rookie"), onDismiss = { dismissed++ }) }
        }

        compose.onNodeWithTag("celebration_dismiss").performClick()

        assertEquals(1, dismissed)
    }

    @Test
    fun withReduceMotionTheCelebrationStillAppears() {
        compose.setContent {
            ManTheme {
                CompositionLocalProvider(LocalReduceMotion provides true) {
                    CelebrationDialog(Celebration.LevelUp(5, 1, 2, "Rookie"), onDismiss = {})
                }
            }
        }

        compose.onNodeWithTag("celebration_dialog").assertIsDisplayed()
    }

    @Test
    fun noCelebrationMeansNoDialog() {
        compose.setContent { ManTheme { CelebrationDialog(null, onDismiss = {}) } }
        compose.onAllNodesWithTag("celebration_dialog").assertCountEquals(0)
    }
}
