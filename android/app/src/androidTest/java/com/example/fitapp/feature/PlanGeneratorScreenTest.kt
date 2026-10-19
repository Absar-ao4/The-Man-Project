package com.example.fitapp.feature

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.feature.generator.domain.EquipmentSetup
import com.example.fitapp.feature.generator.domain.FiveDaySplit
import com.example.fitapp.feature.generator.domain.GeneratedDay
import com.example.fitapp.feature.generator.domain.GeneratedExercise
import com.example.fitapp.feature.generator.domain.GeneratedPlan
import com.example.fitapp.feature.generator.domain.MuscleVolume
import com.example.fitapp.feature.generator.domain.PlanAnswers
import com.example.fitapp.feature.generator.ui.ExplainState
import com.example.fitapp.feature.generator.ui.GeneratorActions
import com.example.fitapp.feature.generator.ui.GeneratorUiState
import com.example.fitapp.feature.generator.ui.PlanGeneratorContent
import com.example.fitapp.feature.profile.domain.FitnessLevel
import com.example.fitapp.feature.profile.domain.Goal
import com.example.fitapp.feature.workout.domain.JointTag
import com.example.fitapp.feature.workout.domain.Muscle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlanGeneratorScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private class Calls {
        val goals = mutableListOf<Goal>()
        val days = mutableListOf<Int>()
        val minutes = mutableListOf<Int>()
        val equipment = mutableListOf<EquipmentSetup>()
        val levels = mutableListOf<FitnessLevel>()
        val splits = mutableListOf<FiveDaySplit>()
        val injuries = mutableListOf<JointTag>()
        val priorities = mutableListOf<Muscle>()
        var generate = 0
        var explain = 0
        var save = 0
        var routines = 0
        val replace = mutableListOf<Boolean>()

        fun actions() =
            GeneratorActions(
                onGoal = { goals += it },
                onDays = { days += it },
                onMinutes = { minutes += it },
                onEquipment = { equipment += it },
                onLevel = { levels += it },
                onFiveDaySplit = { splits += it },
                onInjury = { injuries += it },
                onPriority = { priorities += it },
                onGenerate = { generate++ },
                onExplain = { explain++ },
                onSave = { save++ },
                onReplacePrevious = { replace += it },
                onOpenRoutines = { routines++ },
            )
    }

    private val plan =
        GeneratedPlan(
            name = "4-day muscle gain plan",
            summary = "4 days a week, about 60 minutes each.",
            days =
                listOf(
                    GeneratedDay(
                        0,
                        "Upper A",
                        "Chest, back",
                        55,
                        listOf(
                            GeneratedExercise("b", "Barbell Bench Press", Muscle.CHEST, 0, 4, 6, 10, 120),
                            GeneratedExercise("r", "Barbell Row", Muscle.BACK, 1, 3, 8, 8, 120),
                        ),
                    ),
                ),
            weeklySets = listOf(MuscleVolume(Muscle.CHEST, 14, 8)),
            notes = listOf("Sessions of 60 minutes cannot hold the full weekly volume."),
        )

    private fun show(
        state: GeneratorUiState,
        calls: Calls = Calls(),
    ) = compose.setContent { ManTheme { PlanGeneratorContent(state, calls.actions()) } }

    private fun ready(
        answers: PlanAnswers = PlanAnswers(),
        withPlan: Boolean = false,
        explain: ExplainState = ExplainState.Idle,
        savedRoutines: Int? = null,
    ) = GeneratorUiState(
        answers = answers,
        loadingAnswers = false,
        plan = if (withPlan) plan else null,
        generatedFor = if (withPlan) answers else null,
        explain = explain,
        savedRoutines = savedRoutines,
    )

    // ------------------------------------------------------------------ questionnaire

    @Test
    fun theCurrentAnswersAreSelected() {
        show(ready(PlanAnswers(goal = Goal.FAT_LOSS, daysPerWeek = 3, sessionMinutes = 45, equipment = EquipmentSetup.HOME_BODYWEIGHT)))

        compose.onNodeWithTag("gen_goal_FAT_LOSS").assertIsSelected()
        compose.onNodeWithTag("gen_days_3").assertIsSelected()
        compose.onNodeWithTag("gen_minutes_45").assertIsSelected()
        compose.onNodeWithTag("gen_equipment_HOME_BODYWEIGHT").assertIsSelected()
    }

    @Test
    fun tappingAnOptionReportsIt() {
        val calls = Calls()
        show(ready(), calls)

        compose.onNodeWithTag("gen_goal_FAT_LOSS").performScrollTo().performClick()
        compose.onNodeWithTag("gen_days_6").performScrollTo().performClick()
        compose.onNodeWithTag("gen_minutes_90").performScrollTo().performClick()
        compose.onNodeWithTag("gen_equipment_DUMBBELLS_ONLY").performScrollTo().performClick()
        compose.onNodeWithTag("gen_level_ADVANCED").performScrollTo().performClick()
        compose.onNodeWithTag("gen_injury_KNEE").performScrollTo().performClick()
        compose.onNodeWithTag("gen_priority_CALVES").performScrollTo().performClick()

        assertEquals(listOf(Goal.FAT_LOSS), calls.goals)
        assertEquals(listOf(6), calls.days)
        assertEquals(listOf(90), calls.minutes)
        assertEquals(listOf(EquipmentSetup.DUMBBELLS_ONLY), calls.equipment)
        assertEquals(listOf(FitnessLevel.ADVANCED), calls.levels)
        assertEquals(listOf(JointTag.KNEE), calls.injuries)
        assertEquals(listOf(Muscle.CALVES), calls.priorities)
    }

    @Test
    fun theFiveDayChoiceOnlyAppearsOnFiveDays() {
        show(ready(PlanAnswers(daysPerWeek = 4)))
        compose.onAllNodesWithTag("gen_split_BRO").assertCountEquals(0)
    }

    @Test
    fun onFiveDaysTheSplitCanBeChosen() {
        val calls = Calls()
        show(ready(PlanAnswers(daysPerWeek = 5)), calls)

        compose.onNodeWithTag("gen_split_BRO").performScrollTo().performClick()

        assertEquals(listOf(FiveDaySplit.BRO), calls.splits)
    }

    @Test
    fun injuriesAndWeakPointsAreMultiSelect() {
        show(ready(PlanAnswers(injuries = setOf(JointTag.KNEE, JointTag.WRIST), priorityMuscles = setOf(Muscle.CHEST, Muscle.BACK))))

        compose.onNodeWithTag("gen_injury_KNEE").assertIsSelected()
        compose.onNodeWithTag("gen_injury_WRIST").assertIsSelected()
        compose.onNodeWithTag("gen_priority_CHEST").assertIsSelected()
        compose.onNodeWithTag("gen_priority_BACK").assertIsSelected()
    }

    @Test
    fun loadingAnswersShowsAPlaceholderNotTheForm() {
        show(GeneratorUiState(loadingAnswers = true))
        compose.onAllNodesWithTag("gen_generate").assertCountEquals(0)
    }

    @Test
    fun generateIsDisabledWhileGenerating() {
        show(ready().copy(generating = true))
        compose.onNodeWithTag("gen_generate").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun generateCallsBackAndAnErrorIsShown() {
        val calls = Calls()
        show(ready().copy(error = "You seem to be offline."), calls)

        compose.onNodeWithTag("gen_error").assertTextEquals("You seem to be offline.")
        compose.onNodeWithTag("gen_generate").performScrollTo().performClick()
        assertEquals(1, calls.generate)
    }

    @Test
    fun theWeakPointLimitMessageIsShown() {
        show(ready().copy(message = "Pick up to 4 weak points."))
        compose.onNodeWithTag("gen_message").performScrollTo().assertIsDisplayed()
    }

    // ------------------------------------------------------------------ the plan

    @Test
    fun aGeneratedPlanShowsDaysExercisesVolumeAndNotes() {
        show(ready(withPlan = true))

        compose.onNodeWithTag("gen_plan").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Barbell Bench Press").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("4 x 6-10").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("3 x 8").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("gen_volume_CHEST").performScrollTo().assertTextEquals("8 of 14")
        compose.onNodeWithTag("gen_note").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithTag("gen_stale").assertCountEquals(0)
    }

    @Test
    fun theButtonSaysGenerateAgainOnceThereIsAPlan() {
        show(ready(withPlan = true))
        compose.onNodeWithText("Generate again").assertExists()
    }

    @Test
    fun changedAnswersMarkThePlanStaleAndBlockSavingAndExplaining() {
        val state = ready(withPlan = true).copy(answers = PlanAnswers(goal = Goal.FAT_LOSS))
        show(state)

        compose.onNodeWithTag("gen_stale").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("gen_save").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("gen_explain").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun savingAndTheReplaceOptionCallBack() {
        val calls = Calls()
        show(ready(withPlan = true), calls)

        compose.onNodeWithTag("gen_replace").performScrollTo().performClick()
        compose.onNodeWithTag("gen_save").performScrollTo().assertIsEnabled().performClick()

        assertEquals(listOf(false), calls.replace)
        assertEquals(1, calls.save)
    }

    @Test
    fun afterSavingTheCountAndALinkToTheRoutinesAppear() {
        val calls = Calls()
        show(ready(withPlan = true, savedRoutines = 2), calls)

        compose.onNodeWithTag("gen_saved").performScrollTo().assertTextEquals("Saved 2 routines.")
        compose.onNodeWithTag("gen_open_routines").performScrollTo().performClick()
        assertEquals(1, calls.routines)
    }

    @Test
    fun oneSavedRoutineIsSingular() {
        show(ready(withPlan = true, savedRoutines = 1))
        compose.onNodeWithTag("gen_saved").performScrollTo().assertTextEquals("Saved 1 routine.")
    }

    // ------------------------------------------------------------------ the AI explanation

    @Test
    fun theExplanationIsOptionalAndSaysItOnlySuggests() {
        val calls = Calls()
        show(ready(withPlan = true), calls)

        compose.onNodeWithText("The suggestions are ideas only", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("gen_explain").performScrollTo().performClick()
        assertEquals(1, calls.explain)
    }

    @Test
    fun anExplanationShowsItsTextIdeasAndTheCountLeft() {
        show(ready(withPlan = true, explain = ExplainState.Done("A simple split.", listOf("Add rows.", "Walk more."), 4)))

        compose.onNodeWithTag("gen_explanation").performScrollTo().assertTextEquals("A simple split.")
        compose.onAllNodesWithTag("gen_suggestion").assertCountEquals(2)
        compose.onNodeWithText("4 explanations left today.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Explain again").assertExists()
    }

    @Test
    fun anExplanationFailureIsShown() {
        show(ready(withPlan = true, explain = ExplainState.Failed("The AI explanation is not available right now.")))
        compose.onNodeWithTag("gen_explain_error").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun explainingShowsProgressAndCannotBeStartedTwice() {
        show(ready(withPlan = true, explain = ExplainState.Loading))

        compose.onNodeWithTag("gen_explain_loading").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("gen_explain").performScrollTo().assertIsNotEnabled()
    }
}
