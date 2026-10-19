package com.example.fitapp.feature

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.feature.profile.domain.FitnessLevel
import com.example.fitapp.feature.workout.domain.Equipment
import com.example.fitapp.feature.workout.domain.Exercise
import com.example.fitapp.feature.workout.domain.ExerciseRecords
import com.example.fitapp.feature.workout.domain.Muscle
import com.example.fitapp.feature.workout.domain.PlanDay
import com.example.fitapp.feature.workout.domain.PlanDetail
import com.example.fitapp.feature.workout.domain.PlanExercise
import com.example.fitapp.feature.workout.domain.PlanSplit
import com.example.fitapp.feature.workout.domain.PlanSummary
import com.example.fitapp.feature.workout.domain.PreviousSet
import com.example.fitapp.feature.workout.domain.RepMaxRecord
import com.example.fitapp.feature.workout.domain.Routine
import com.example.fitapp.feature.workout.domain.RoutineExercise
import com.example.fitapp.feature.workout.domain.SessionSet
import com.example.fitapp.feature.workout.domain.SessionSummary
import com.example.fitapp.feature.workout.domain.SetType
import com.example.fitapp.feature.workout.domain.WorkoutSession
import com.example.fitapp.feature.workout.ui.EditableRoutineExercise
import com.example.fitapp.feature.workout.ui.ExerciseDetailContent
import com.example.fitapp.feature.workout.ui.ExerciseDetailUiState
import com.example.fitapp.feature.workout.ui.HistoryContent
import com.example.fitapp.feature.workout.ui.HistoryDetailContent
import com.example.fitapp.feature.workout.ui.HistoryDetailUiState
import com.example.fitapp.feature.workout.ui.HistoryUiState
import com.example.fitapp.feature.workout.ui.LibraryContent
import com.example.fitapp.feature.workout.ui.LibraryFilters
import com.example.fitapp.feature.workout.ui.LibraryUiState
import com.example.fitapp.feature.workout.ui.PlanDetailContent
import com.example.fitapp.feature.workout.ui.PlanDetailUiState
import com.example.fitapp.feature.workout.ui.PlanFilters
import com.example.fitapp.feature.workout.ui.PlansContent
import com.example.fitapp.feature.workout.ui.PlansUiState
import com.example.fitapp.feature.workout.ui.RecordsContent
import com.example.fitapp.feature.workout.ui.RecordsUiState
import com.example.fitapp.feature.workout.ui.RestBanner
import com.example.fitapp.feature.workout.ui.RoutineEditorContent
import com.example.fitapp.feature.workout.ui.RoutineEditorUiState
import com.example.fitapp.feature.workout.ui.RoutineForm
import com.example.fitapp.feature.workout.ui.RoutinesContent
import com.example.fitapp.feature.workout.ui.RoutinesUiState
import com.example.fitapp.feature.workout.ui.SessionActions
import com.example.fitapp.feature.workout.ui.SessionContent
import com.example.fitapp.feature.workout.ui.SessionUiState
import com.example.fitapp.feature.workout.ui.WorkoutHubActions
import com.example.fitapp.feature.workout.ui.WorkoutHubContent
import com.example.fitapp.feature.workout.ui.WorkoutHubUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant

class WorkoutScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val bench = Exercise("bench", "Barbell Bench Press", "Press it.", Muscle.CHEST, listOf(Muscle.TRICEPS), Equipment.BARBELL, emptyList(), true, false)

    private fun set(
        id: String,
        index: Int,
        completed: Boolean = false,
        pr: Boolean = false,
        kg: Double = 80.0,
        reps: Int = 8,
    ) = SessionSet(id, "bench", "Barbell Bench Press", index, SetType.NORMAL, kg, reps, null, completed, null, pr, 90)

    private fun session(
        sets: List<SessionSet>,
        restEndsAt: Instant? = null,
    ) = WorkoutSession(
        id = "s1",
        name = "Push day",
        startedAt = Instant.parse("2026-03-02T09:00:00Z"),
        endedAt = null,
        routineId = null,
        completed = false,
        sets = sets,
        restEndsAt = restEndsAt,
        restTotalSeconds = 90,
    )

    private fun noopActions() = SessionActions({ _, _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    private fun hubActions(
        startEmpty: () -> Unit = {},
        resume: () -> Unit = {},
    ) = WorkoutHubActions(resume, startEmpty, {}, {}, {}, {}, {}, {})

    // ------------------------------------------------------------ hub

    @Test
    fun hubOffersToStartAWorkout() {
        var started = false
        compose.setContent {
            ManTheme { WorkoutHubContent(WorkoutHubUiState.Content(null, emptyList()), hubActions(startEmpty = { started = true })) }
        }
        compose.onNodeWithText("No routines yet").assertIsDisplayed()
        compose.onNodeWithTag("hub_start_empty").performClick()
        assertTrue(started)
    }

    @Test
    fun hubOffersToResumeARunningWorkout() {
        var resumed = false
        compose.setContent {
            ManTheme {
                WorkoutHubContent(
                    WorkoutHubUiState.Content(session(listOf(set("a", 0, completed = true), set("b", 1))), emptyList()),
                    hubActions(resume = { resumed = true }),
                )
            }
        }
        compose.onNodeWithText("Workout in progress").assertIsDisplayed()
        compose.onNodeWithText("Push day · 1/2 sets done").assertIsDisplayed()
        compose.onNodeWithTag("hub_resume").performClick()
        assertTrue(resumed)
    }

    // ------------------------------------------------------------ library

    @Test
    fun libraryListsExercisesAndOpensOne() {
        var opened: String? = null
        compose.setContent {
            ManTheme {
                LibraryContent(LibraryUiState.Content(LibraryFilters(), listOf(bench)), {}, {}, {}, {}, { opened = it })
            }
        }
        compose.onNodeWithText("Barbell Bench Press").performClick()
        assertEquals("bench", opened)
    }

    @Test
    fun libraryShowsAnErrorWithRetry() {
        var retried = false
        compose.setContent {
            ManTheme { LibraryContent(LibraryUiState.Error(LibraryFilters(), "You seem to be offline."), {}, {}, {}, { retried = true }, {}) }
        }
        compose.onNodeWithText("You seem to be offline.").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        assertTrue(retried)
    }

    @Test
    fun libraryEmptySearchSaysSo() {
        compose.setContent { ManTheme { LibraryContent(LibraryUiState.Content(LibraryFilters(query = "zzz"), emptyList()), {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("No exercises found").assertIsDisplayed()
    }

    @Test
    fun exerciseDetailShowsInstructionsAndRecords() {
        val records = ExerciseRecords("bench", "Barbell Bench Press", 107.7, Instant.parse("2026-03-01T10:00:00Z"), listOf(RepMaxRecord(8, 85.0, Instant.EPOCH)))
        compose.setContent { ManTheme { ExerciseDetailContent(ExerciseDetailUiState.Content(bench, records, emptyList())) } }
        compose.onNodeWithText("How to do it").assertIsDisplayed()
        compose.onNodeWithText("Press it.").assertIsDisplayed()
        compose.onNodeWithText("Estimated 1RM").assertIsDisplayed()
    }

    // ------------------------------------------------------------ plans

    @Test
    fun plansListAndOpen() {
        var opened: String? = null
        val plan = PlanSummary("p1", "Push Pull Legs (Beginner)", PlanSplit.PPL, FitnessLevel.BEGINNER, "Three sessions.", 3)
        compose.setContent {
            ManTheme { PlansContent(PlansUiState.Content(PlanFilters(), listOf(plan)), {}, {}, {}, { opened = it }) }
        }
        compose.onNodeWithText("Push Pull Legs (Beginner)").performClick()
        assertEquals("p1", opened)
    }

    @Test
    fun planDetailCanSaveOrStartADay() {
        var saved: String? = null
        var started: String? = null
        val summary = PlanSummary("p1", "PPL", PlanSplit.PPL, FitnessLevel.BEGINNER, "Desc", 3)
        val day = PlanDay("d1", 0, "Push", "Chest", listOf(PlanExercise("bench", "Barbell Bench Press", Muscle.CHEST, 0, 3, 8, 12, 120)))
        compose.setContent {
            ManTheme {
                PlanDetailContent(PlanDetailUiState.Content(PlanDetail(summary, listOf(day))), { saved = it }, { started = it })
            }
        }
        compose.onNodeWithText("3 × 8-12").assertIsDisplayed()
        compose.onNodeWithTag("plan_save_0").performClick()
        compose.onNodeWithTag("plan_start_0").performClick()
        assertEquals("d1", saved)
        assertEquals("d1", started)
    }

    // ------------------------------------------------------------ routines

    @Test
    fun routinesEmptyStateOffersCreate() {
        var created = false
        compose.setContent {
            ManTheme { RoutinesContent(RoutinesUiState.Content(emptyList()), { created = true }, {}, {}, {}) }
        }
        compose.onNodeWithText("No routines yet").assertIsDisplayed()
        compose.onNodeWithText("Create routine").performClick()
        assertTrue(created)
    }

    @Test
    fun routinesListStartsAndConfirmsDelete() {
        var started: String? = null
        var deleted: String? = null
        val routine = Routine("r1", "Push", "", null, listOf(RoutineExercise("re", "bench", "Barbell Bench Press", 0, 3, 6, 10, 120)), Instant.EPOCH)
        compose.setContent {
            ManTheme { RoutinesContent(RoutinesUiState.Content(listOf(routine)), {}, {}, { started = it }, { deleted = it }) }
        }
        compose.onNodeWithText("Start").performClick()
        assertEquals("r1", started)

        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete routine?").assertIsDisplayed()
        assertEquals(null, deleted) // nothing is deleted until the dialog is confirmed

        compose.onNodeWithText("Cancel").performClick()
        assertEquals(null, deleted)
    }

    @Test
    fun routineEditorShowsFormAndSaves() {
        var saved = false
        val form =
            RoutineForm(
                id = "r1",
                name = "Push",
                exercises = listOf(EditableRoutineExercise("re", "bench", "Barbell Bench Press", 3, 6, 10, 120)),
            )
        compose.setContent {
            ManTheme {
                RoutineEditorContent(
                    state = RoutineEditorUiState.Editing(form, isNew = true),
                    onNameChange = {},
                    onNotesChange = {},
                    onAddExercise = {},
                    onRemove = {},
                    onMove = { _, _ -> },
                    onUpdate = { _, _, _, _, _ -> },
                    onSave = { saved = true },
                )
            }
        }
        compose.onNodeWithText("Barbell Bench Press").assertIsDisplayed()
        compose.onNodeWithTag("routine_save").performScrollTo().performClick()
        assertTrue(saved)
    }

    // ------------------------------------------------------------ session

    @Test
    fun sessionShowsSetsPreviousPerformanceAndMarksDone() {
        val toggled = mutableListOf<String>()
        val actions = SessionActions({ _, _, _ -> }, { toggled += it }, {}, {}, {}, {}, {}, {}, {}, {}, {})
        compose.setContent {
            ManTheme {
                SessionContent(
                    SessionUiState.Active(
                        session(listOf(set("a", 0), set("b", 1))),
                        mapOf("bench" to listOf(PreviousSet(77.5, 8), PreviousSet(75.0, 8))),
                    ),
                    actions,
                    onLeave = {},
                )
            }
        }
        compose.onNodeWithText("Barbell Bench Press").assertIsDisplayed()
        compose.onNodeWithText("77.5 × 8").assertIsDisplayed()
        compose.onNodeWithText("75 × 8").assertIsDisplayed()
        compose.onNodeWithTag("done_a").performClick()
        assertEquals(listOf("a"), toggled)
    }

    @Test
    fun sessionShowsAPrBadgeOnACompletedPrSet() {
        compose.setContent {
            ManTheme {
                SessionContent(
                    SessionUiState.Active(session(listOf(set("a", 0, completed = true, pr = true))), emptyMap()),
                    noopActions(),
                    onLeave = {},
                )
            }
        }
        compose.onNodeWithText("PR").assertIsDisplayed()
    }

    @Test
    fun finishingAsksForConfirmationAndExplainsDroppedSets() {
        var finished = false
        val actions = SessionActions({ _, _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, { finished = true }, {})
        compose.setContent {
            ManTheme {
                SessionContent(
                    SessionUiState.Active(session(listOf(set("a", 0, completed = true), set("b", 1))), emptyMap()),
                    actions,
                    onLeave = {},
                )
            }
        }
        compose.onNodeWithTag("session_finish").performScrollTo().performClick()
        compose.onNodeWithText("Finish workout?").assertIsDisplayed()
        compose.onNodeWithText("1 unfinished set(s) will be dropped. 1 completed set(s) are saved.").assertIsDisplayed()
        assertTrue(!finished)
        compose.onNodeWithText("Finish").performClick()
        assertTrue(finished)
    }

    @Test
    fun discardingAsksForConfirmation() {
        var discarded = false
        val actions = SessionActions({ _, _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, { discarded = true })
        compose.setContent {
            ManTheme {
                SessionContent(SessionUiState.Active(session(listOf(set("a", 0))), emptyMap()), actions, onLeave = {})
            }
        }
        compose.onNodeWithTag("session_discard").performScrollTo().performClick()
        compose.onNodeWithText("Discard workout?").assertIsDisplayed()
        compose.onNodeWithText("Discard").performClick()
        assertTrue(discarded)
    }

    @Test
    fun noActiveSessionOffersAWayBack() {
        var left = false
        compose.setContent { ManTheme { SessionContent(SessionUiState.NoActiveSession, noopActions(), onLeave = { left = true }) } }
        compose.onNodeWithText("No workout in progress").assertIsDisplayed()
        compose.onNodeWithText("Back to workouts").performClick()
        assertTrue(left)
    }

    @Test
    fun restBannerCountsDownAndOffersAdjustments() {
        var adjust = 0
        var skipped = false
        compose.setContent {
            ManTheme {
                RestBanner(
                    endsAtMillis = System.currentTimeMillis() + 90_000,
                    totalSeconds = 90,
                    onSkip = { skipped = true },
                    onAdjust = { adjust += it },
                    onFinished = {},
                )
            }
        }
        compose.onNodeWithTag("rest_clock").assertIsDisplayed()
        compose.onNodeWithTag("rest_plus").performClick()
        compose.onNodeWithTag("rest_minus").performClick()
        compose.onNodeWithTag("rest_skip").performClick()
        assertEquals(0, adjust) // +15 and -15
        assertTrue(skipped)
    }

    @Test
    fun restBannerReportsWhenTheTimeIsUp() {
        var finished = false
        compose.setContent {
            ManTheme {
                RestBanner(
                    endsAtMillis = System.currentTimeMillis() - 1,
                    totalSeconds = 30,
                    onSkip = {},
                    onAdjust = {},
                    onFinished = { finished = true },
                )
            }
        }
        compose.waitUntil(timeoutMillis = 3_000) { finished }
        assertTrue(finished)
    }

    // ------------------------------------------------------------ history and records

    @Test
    fun historyEmptyStateExplainsWhatToDo() {
        compose.setContent { ManTheme { HistoryContent(HistoryUiState.Content(emptyList()), {}) } }
        compose.onNodeWithText("No workouts yet").assertIsDisplayed()
    }

    @Test
    fun historyListsSessionsWithDurationAndVolume() {
        var opened: String? = null
        val summary = SessionSummary("s1", "Push day", Instant.parse("2026-03-02T09:00:00Z"), Duration.ofMinutes(65), 3, 9, 4200.0, 2)
        compose.setContent { ManTheme { HistoryContent(HistoryUiState.Content(listOf(summary)), { opened = it }) } }
        compose.onNodeWithText("2 PRs").assertIsDisplayed()
        compose.onNodeWithText("1h 05m · 9 sets · 4,200 kg").assertIsDisplayed()
        compose.onNodeWithTag("history_s1").performClick()
        assertEquals("s1", opened)
    }

    @Test
    fun historyDetailShowsEachSet() {
        val finished = session(listOf(set("a", 0, completed = true, kg = 85.0, reps = 8, pr = true))).copy(endedAt = Instant.parse("2026-03-02T10:00:00Z"))
        compose.setContent { ManTheme { HistoryDetailContent(HistoryDetailUiState.Content(finished)) } }
        compose.onNodeWithText("1h 00m · 1 sets · 680 kg").assertIsDisplayed()
        compose.onNodeWithText("85 kg × 8  PR").assertIsDisplayed()
    }

    @Test
    fun recordsShowBestEstimatedOneRepMax() {
        val record = ExerciseRecords("bench", "Barbell Bench Press", 107.7, Instant.parse("2026-03-01T10:00:00Z"), listOf(RepMaxRecord(8, 85.0, Instant.EPOCH)))
        compose.setContent { ManTheme { RecordsContent(RecordsUiState.Content(listOf(record)), {}) } }
        compose.onNodeWithText("107.7 kg").assertIsDisplayed()
        compose.onNodeWithText("Heaviest set: 85 kg × 8").assertIsDisplayed()
    }

    @Test
    fun recordsEmptyStateExplainsBaselines() {
        compose.setContent { ManTheme { RecordsContent(RecordsUiState.Content(emptyList()), {}) } }
        compose.onNodeWithText("No records yet").assertIsDisplayed()
    }
}
