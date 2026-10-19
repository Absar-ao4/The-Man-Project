package com.example.fitapp.feature

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertCountEquals
import com.example.fitapp.core.animation.LocalReduceMotion
import com.example.fitapp.core.designsystem.LockInTheme
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.feature.gamification.domain.XpLine
import com.example.fitapp.feature.lockin.domain.LockinDay
import com.example.fitapp.feature.lockin.domain.LockinLine
import com.example.fitapp.feature.lockin.domain.LockinMethod
import com.example.fitapp.feature.lockin.domain.LockinProgram
import com.example.fitapp.feature.lockin.ui.CurrentSet
import com.example.fitapp.feature.lockin.ui.LockinEffects
import com.example.fitapp.feature.lockin.ui.LockinHud
import com.example.fitapp.feature.lockin.ui.LockinProgramActions
import com.example.fitapp.feature.lockin.ui.LockinProgramContent
import com.example.fitapp.feature.lockin.ui.LockinProgramUiState
import com.example.fitapp.feature.lockin.ui.LockinProgramsContent
import com.example.fitapp.feature.lockin.ui.LockinProgramsUiState
import com.example.fitapp.feature.lockin.ui.LockinSessionActions
import com.example.fitapp.feature.lockin.ui.LockinSessionContent
import com.example.fitapp.feature.lockin.ui.LockinSessionUiState
import com.example.fitapp.feature.lockin.ui.LockinSummaryContent
import com.example.fitapp.feature.lockin.ui.LockinSummaryUiState
import com.example.fitapp.feature.lockin.ui.ProgramCard
import com.example.fitapp.feature.lockin.ui.components.HoldToExitButton
import com.example.fitapp.feature.lockin.ui.components.LockinHaptics
import com.example.fitapp.feature.profile.domain.FitnessLevel
import com.example.fitapp.feature.workout.domain.PreviousSet
import com.example.fitapp.feature.workout.domain.SessionSet
import com.example.fitapp.feature.workout.domain.SetType
import com.example.fitapp.feature.workout.domain.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant

class LockinUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val silent = LockinHaptics(null)

    // ------------------------------------------------------------------ fixtures

    private fun line(
        method: LockinMethod = LockinMethod.STRAIGHT,
        drops: Int = 0,
    ) = LockinLine("bench", "Barbell Bench Press", "CHEST", 10, 10, 10, 60, 7.5, method, null, drops, listOf("Brace your core.", "Control the way down.", "Third cue."))

    private fun program(
        level: FitnessLevel = FitnessLevel.INTERMEDIATE,
        days: List<LockinDay> = listOf(LockinDay(0, "Chest and Back", "10x10", 60, listOf(line()))),
    ) = LockinProgram("p1", "German Volume Training", "Ten sets of ten.", "Same weight every set.", level, 60, days.size, days)

    private fun setOf(
        id: String,
        index: Int,
        completed: Boolean = false,
    ) = SessionSet(
        id = id,
        exerciseId = "bench",
        exerciseName = "Barbell Bench Press",
        setIndex = index,
        setType = SetType.NORMAL,
        weightKg = 80.0,
        reps = 8,
        rpe = null,
        completed = completed,
        completedAt = if (completed) Instant.parse("2026-03-02T09:10:00Z") else null,
        isPr = false,
        restSeconds = 60,
    )

    private fun workout(sets: List<SessionSet>) =
        WorkoutSession(
            id = "s1",
            name = "German Volume Training: Chest and Back",
            startedAt = Instant.parse("2026-03-02T09:00:00Z"),
            endedAt = null,
            routineId = null,
            completed = false,
            sets = sets,
            restEndsAt = null,
            restTotalSeconds = 0,
            mode = "LOCKIN",
            lockinProgramId = "p1",
            maxCombo = 0,
        )

    private fun active(
        countdown: Int? = null,
        current: Boolean = true,
        restRemaining: Int = 0,
        method: LockinMethod = LockinMethod.STRAIGHT,
        drops: Int = 0,
        number: Int = 3,
        total: Int = 10,
        combo: Int = 2,
    ): LockinSessionUiState.Active {
        val set = setOf("set3", 2)
        return LockinSessionUiState.Active(
            session = workout(listOf(set)),
            program = program(),
            countdown = countdown,
            current = if (current) CurrentSet(set, line(method, drops), number, total, PreviousSet(77.5, 8)) else null,
            upNext = "Barbell Row",
            restRemainingSeconds = restRemaining,
            restTotalSeconds = if (restRemaining > 0) 60 else 0,
            hud = LockinHud(setsDone = 2, setsTotal = 10, combo = combo, maxCombo = 3, comboWindow = 0.6f, comboMultiplier = 1.2, predictedXp = 6),
        )
    }

    private class Counter {
        var complete = 0
        var undo = 0
        var skipRest = 0
        val restAdjust = mutableListOf<Int>()
        val weight = mutableListOf<Double>()
        val reps = mutableListOf<Int>()
        var finish = 0
        var quit = 0
        var close = 0

        fun actions() =
            LockinSessionActions(
                onComplete = { complete++ },
                onUndo = { undo++ },
                onWeight = { weight += it },
                onReps = { reps += it },
                onSkipRest = { skipRest++ },
                onAdjustRest = { restAdjust += it },
                onFinish = { finish++ },
                onQuit = { quit++ },
                onClose = { close++ },
            )
    }

    private fun showSession(
        state: LockinSessionUiState,
        counter: Counter = Counter(),
        reduceMotion: Boolean = false,
    ) = compose.setContent {
        LockInTheme {
            CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
                LockinSessionContent(state, counter.actions(), LockinEffects(), silent)
            }
        }
    }

    // ------------------------------------------------------------------ session

    @Test
    fun theCurrentSetShowsTheExerciseGuidanceAndAHugeDoneButton() {
        val counter = Counter()
        showSession(active(), counter)

        compose.onNodeWithTag("lockin_exercise").assertTextEquals("Barbell Bench Press")
        compose.onNodeWithTag("lockin_set_number").assertTextEquals("Set 3 of 10")
        compose.onNodeWithText("Brace your core.").assertIsDisplayed()
        compose.onNodeWithText("Control the way down.").assertIsDisplayed()
        compose.onNodeWithText("Last time: 77.5 kg x 8").assertIsDisplayed()

        compose.onNodeWithTag("lockin_done").performScrollTo().performClick()
        assertEquals(1, counter.complete)
    }

    @Test
    fun onlyTwoCuesAreShownDuringASet() {
        showSession(active())
        compose.onAllNodesWithTag("lockin_exercise").assertCountEquals(1)
        compose.onAllNodesWithText("Third cue.").assertCountEquals(0)
    }

    @Test
    fun weightAndRepsCanBeAdjusted() {
        val counter = Counter()
        showSession(active(), counter)

        compose.onNodeWithTag("lockin_weight_plus").performScrollTo().performClick()
        compose.onNodeWithTag("lockin_weight_minus").performScrollTo().performClick()
        compose.onNodeWithTag("lockin_reps_plus").performScrollTo().performClick()
        compose.onNodeWithTag("lockin_reps_minus").performScrollTo().performClick()

        assertEquals(listOf(2.5, -2.5), counter.weight)
        assertEquals(listOf(1, -1), counter.reps)
        compose.onNodeWithTag("lockin_weight_value").assertTextEquals("80 kg")
        compose.onNodeWithTag("lockin_reps_value").assertTextEquals("8")
    }

    @Test
    fun theComboAndProgressAreAlwaysVisible() {
        showSession(active(combo = 4))

        compose.onNodeWithTag("lockin_combo_value").assertTextEquals("x4")
        compose.onNodeWithTag("lockin_progress").assertIsDisplayed()
    }

    @Test
    fun aDropSetExplainsWhatToDoOnTheLastSet() {
        showSession(active(method = LockinMethod.DROP_SET, drops = 2, number = 10, total = 10))
        compose.onNodeWithText("Last set: go to failure, drop the weight 2 times and keep going.").assertIsDisplayed()
    }

    @Test
    fun theCountdownHidesTheSetsAndSaysLockIn() {
        showSession(active(countdown = 0))

        compose.onNodeWithTag("lockin_countdown").assertIsDisplayed()
        compose.onNodeWithTag("lockin_countdown_text").assertTextEquals("LOCK IN")
        compose.onAllNodesWithTag("lockin_done").assertCountEquals(0)
    }

    @Test
    fun theCountdownShowsThreeTwoOne() {
        showSession(active(countdown = 3))
        compose.onNodeWithTag("lockin_countdown_text").assertTextEquals("3")
    }

    @Test
    fun theCountdownStillWorksWithReduceMotion() {
        showSession(active(countdown = 2), reduceMotion = true)
        compose.onNodeWithTag("lockin_countdown_text").assertTextEquals("2")
    }

    @Test
    fun restShowsTheTimerTheNextExerciseAndTheControls() {
        val counter = Counter()
        showSession(active(restRemaining = 42), counter)

        compose.onNodeWithTag("lockin_rest_seconds").assertTextEquals("42")
        compose.onNodeWithText("Up next: Barbell Bench Press, set 3 of 10").assertIsDisplayed()
        compose.onAllNodesWithTag("lockin_done").assertCountEquals(0)

        compose.onNodeWithTag("lockin_rest_minus").performClick()
        compose.onNodeWithTag("lockin_rest_plus").performClick()
        compose.onNodeWithTag("lockin_rest_skip").performClick()
        assertEquals(listOf(-15, 15), counter.restAdjust)
        assertEquals(1, counter.skipRest)
    }

    @Test
    fun theLastTenSecondsOfRestStillShowTheTimerWithReduceMotion() {
        showSession(active(restRemaining = 7), reduceMotion = true)
        compose.onNodeWithTag("lockin_rest_seconds").assertTextEquals("7")
    }

    @Test
    fun whenEverySetIsDoneTheFinishButtonAppears() {
        val counter = Counter()
        showSession(active(current = false), counter)

        compose.onNodeWithTag("lockin_finish").assertIsDisplayed()
        compose.onNodeWithTag("lockin_finish").performClick()
        assertEquals(1, counter.finish)
        compose.onAllNodesWithTag("lockin_done").assertCountEquals(0)
    }

    @Test
    fun undoIsOfferedOnceASetIsDone() {
        val counter = Counter()
        showSession(active(), counter)

        compose.onNodeWithTag("lockin_undo").performScrollTo().performClick()
        assertEquals(1, counter.undo)
    }

    @Test
    fun noSessionOffersWayBack() {
        val counter = Counter()
        showSession(LockinSessionUiState.NoSession, counter)

        compose.onNodeWithTag("lockin_no_session_back").performClick()
        assertEquals(1, counter.close)
    }

    // ------------------------------------------------------------------ the exit flow

    @Test
    fun theExitButtonIsThereDuringTheCountdownToo() {
        showSession(active(countdown = 3))
        compose.onNodeWithTag("lockin_hold_exit").assertIsDisplayed()
    }

    @Test
    fun aQuickTapOnExitDoesNothing() {
        val counter = Counter()
        showSession(active(), counter)

        compose.onNodeWithTag("lockin_hold_exit").performClick()

        assertEquals(0, counter.quit)
    }

    @Test
    fun holdingExitForTwoSecondsQuitsTheSession() {
        val counter = Counter()
        showSession(active(), counter)
        compose.mainClock.autoAdvance = false

        compose.onNodeWithTag("lockin_hold_exit").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("not yet after one second", 0, counter.quit)
        compose.mainClock.advanceTimeBy(1_100)
        assertEquals(1, counter.quit)
        compose.onNodeWithTag("lockin_hold_exit").performTouchInput { up() }
    }

    @Test
    fun lettingGoEarlyStartsTheHoldOver() {
        var exits = 0
        compose.setContent { ManTheme { HoldToExitButton(onExit = { exits++ }) } }
        compose.mainClock.autoAdvance = false

        compose.onNodeWithTag("lockin_hold_exit").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_500)
        compose.onNodeWithTag("lockin_hold_exit").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(3_000)
        assertEquals("released at 1.5 s: no exit", 0, exits)

        compose.onNodeWithTag("lockin_hold_exit").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("a new hold starts from zero", 0, exits)
        compose.mainClock.advanceTimeBy(1_100)
        assertEquals(1, exits)
        compose.onNodeWithTag("lockin_hold_exit").performTouchInput { up() }
    }

    @Test
    fun screenReaderUsersGetAnActionThatExitsAtOnce() {
        var exits = 0
        compose.setContent { ManTheme { HoldToExitButton(onExit = { exits++ }) } }

        val node = compose.onNodeWithTag("lockin_hold_exit").fetchSemanticsNode()
        val action = node.config[SemanticsActions.CustomActions].single()
        compose.runOnUiThread { action.action() }

        assertEquals(1, exits)
    }

    // ------------------------------------------------------------------ programs

    private fun card(
        level: FitnessLevel,
        user: FitnessLevel,
    ): ProgramCard {
        val p = program(level)
        return ProgramCard(p, p.isAvailableTo(user), p.lockedReason(user))
    }

    @Test
    fun theProgramListShowsLockedProgramsWithTheirReason() {
        var opened: String? = null
        compose.setContent {
            ManTheme {
                LockinProgramsContent(
                    state = LockinProgramsUiState.Content(listOf(card(FitnessLevel.ADVANCED, FitnessLevel.INTERMEDIATE)), refreshing = false),
                    onOpenProgram = { opened = it },
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText("German Volume Training").assertIsDisplayed()
        compose.onNodeWithTag("lockin_locked_p1").assertTextEquals("Locked: Needs advanced level or above.")
        compose.onNodeWithTag("lockin_program_p1").performClick()
        assertEquals("p1", opened)
    }

    @Test
    fun theProgramListHasAnErrorStateWithRetry() {
        var retries = 0
        compose.setContent {
            ManTheme { LockinProgramsContent(LockinProgramsUiState.Error("Offline"), onOpenProgram = {}, onRetry = { retries++ }) }
        }

        compose.onNodeWithText("Offline").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }

    // ------------------------------------------------------------------ program detail

    private class ProgramCounter {
        val days = mutableListOf<Int>()
        var starts = 0
        var dismissed = 0
        var acknowledged = 0

        fun actions() =
            LockinProgramActions(
                onSelectDay = { days += it },
                onStart = { starts++ },
                onDismissWarning = { dismissed++ },
                onAcknowledgeWarning = { acknowledged++ },
                onDoNotDisturb = {},
                onPinScreen = {},
                onGrantDoNotDisturbAccess = {},
            )
    }

    private fun content(
        program: LockinProgram = program(),
        available: Boolean = true,
        showWarning: Boolean = false,
        workoutRunning: Boolean = false,
        dnd: Boolean = false,
        dndAccess: Boolean = true,
    ) = LockinProgramUiState.Content(
        program = program,
        available = available,
        lockedReason = if (available) null else "Needs intermediate level or above.",
        selectedDay = 0,
        workoutRunning = workoutRunning,
        showWarning = showWarning,
        doNotDisturb = dnd,
        doNotDisturbAccess = dndAccess,
        pinScreen = false,
    )

    private fun showProgram(
        state: LockinProgramUiState,
        counter: ProgramCounter = ProgramCounter(),
    ) = compose.setContent { ManTheme { LockinProgramContent(state, counter.actions()) } }

    @Test
    fun theProgramShowsItsInstructionsAndEveryExerciseWithCues() {
        showProgram(content())

        compose.onNodeWithText("Same weight every set.").assertIsDisplayed()
        compose.onNodeWithText("Barbell Bench Press").assertIsDisplayed()
        compose.onNodeWithText("• Brace your core.").assertIsDisplayed()
        compose.onNodeWithTag("lockin_start").assertIsEnabled()
    }

    @Test
    fun aLockedProgramCannotBeStarted() {
        showProgram(content(available = false))

        compose.onNodeWithTag("lockin_locked_reason").assertIsDisplayed()
        compose.onNodeWithTag("lockin_start").assertIsNotEnabled()
    }

    @Test
    fun aRunningWorkoutBlocksTheStart() {
        showProgram(content(workoutRunning = true))

        compose.onNodeWithTag("lockin_workout_running").assertIsDisplayed()
        compose.onNodeWithTag("lockin_start").assertIsNotEnabled()
    }

    @Test
    fun aProgramWithoutDaysExplainsItIsStillDownloading() {
        showProgram(content(program = program(days = emptyList())))

        compose.onNodeWithTag("lockin_no_detail").assertIsDisplayed()
        compose.onNodeWithTag("lockin_start").assertIsNotEnabled()
    }

    @Test
    fun daysCanBePickedWhenThereAreSeveral() {
        val counter = ProgramCounter()
        val days = listOf(LockinDay(0, "Day A", "", 30, listOf(line())), LockinDay(1, "Day B", "", 30, listOf(line())))
        showProgram(content(program = program(days = days)), counter)

        compose.onNodeWithText("Day B").performClick()

        assertEquals(listOf(1), counter.days)
    }

    @Test
    fun startingCallsBack() {
        val counter = ProgramCounter()
        showProgram(content(), counter)

        compose.onNodeWithTag("lockin_start").performClick()

        assertEquals(1, counter.starts)
    }

    @Test
    fun theHealthWarningNeedsTheBoxTickedBeforeContinuing() {
        val counter = ProgramCounter()
        showProgram(content(showWarning = true), counter)

        compose.onNodeWithTag("lockin_warning_continue").assertIsNotEnabled()
        compose.onNodeWithTag("lockin_warning_checkbox").performClick()
        compose.onNodeWithTag("lockin_warning_continue").assertIsEnabled()
        compose.onNodeWithTag("lockin_warning_continue").performClick()

        assertEquals(1, counter.acknowledged)
    }

    @Test
    fun theHealthWarningCanBeDismissed() {
        val counter = ProgramCounter()
        showProgram(content(showWarning = true), counter)

        compose.onNodeWithText("Not now").performClick()

        assertEquals(1, counter.dismissed)
        assertEquals(0, counter.acknowledged)
    }

    @Test
    fun doNotDisturbWithoutAccessOffersToGrantIt() {
        showProgram(content(dnd = true, dndAccess = false))
        compose.onNodeWithTag("lockin_grant_dnd").assertIsDisplayed()
    }

    @Test
    fun doNotDisturbWithAccessNeedsNoExtraStep() {
        showProgram(content(dnd = true, dndAccess = true))
        compose.onAllNodesWithTag("lockin_grant_dnd").assertCountEquals(0)
    }

    // ------------------------------------------------------------------ summary

    private fun summary(
        completed: Boolean = true,
        xp: Int = 190,
    ) = LockinSummaryUiState.Content(
        name = "German Volume Training: Chest and Back",
        completed = completed,
        duration = Duration.ofMinutes(45),
        setsDone = 20,
        volumeKg = 12_345.0,
        prCount = 2,
        maxCombo = 7,
        xpLines = listOf(XpLine("Workout complete", 50), XpLine("Lock-in bonus", 140)),
        predictedXp = xp,
    )

    @Test
    fun theXpCounterRollsUpToTheEstimate() {
        compose.mainClock.autoAdvance = false
        compose.setContent { LockInTheme { LockinSummaryContent(summary(), onDone = {}, onShare = {}) } }

        compose.mainClock.advanceTimeBy(100)
        compose.mainClock.advanceTimeBy(2_500)

        compose.onNodeWithTag("lockin_summary_xp").assertTextEquals("+190 XP")
    }

    @Test
    fun withReduceMotionTheCounterShowsTheNumberAtOnce() {
        compose.setContent {
            LockInTheme {
                CompositionLocalProvider(LocalReduceMotion provides true) { LockinSummaryContent(summary(xp = 120), onDone = {}, onShare = {}) }
            }
        }

        compose.waitForIdle()
        compose.onNodeWithTag("lockin_summary_xp").assertTextEquals("+120 XP")
    }

    @Test
    fun theSummaryListsTheXpLinesAndTheSessionNumbers() {
        compose.setContent { LockInTheme { LockinSummaryContent(summary(), onDone = {}, onShare = {}) } }

        compose.onNodeWithTag("lockin_summary_title").assertTextEquals("SESSION COMPLETE")
        compose.onNodeWithText("Lock-in bonus").assertIsDisplayed()
        compose.onNodeWithText("45 min").assertIsDisplayed()
        compose.onNodeWithText("x7").assertIsDisplayed()
        compose.onNodeWithTag("lockin_share_card").assertExists()
    }

    @Test
    fun leavingEarlyExplainsWhatWasLost() {
        compose.setContent { LockInTheme { LockinSummaryContent(summary(completed = false), onDone = {}, onShare = {}) } }

        compose.onNodeWithTag("lockin_summary_title").assertTextEquals("ENDED EARLY")
        compose.onNodeWithTag("lockin_early_note").assertIsDisplayed()
    }

    @Test
    fun shareAndDoneCallBack() {
        var shared = 0
        var done = 0
        compose.setContent { LockInTheme { LockinSummaryContent(summary(), onDone = { done++ }, onShare = { shared++ }) } }

        compose.onNodeWithTag("lockin_share").performClick()
        compose.onNodeWithTag("lockin_done_summary").performClick()

        assertEquals(1, shared)
        assertEquals(1, done)
    }
}
