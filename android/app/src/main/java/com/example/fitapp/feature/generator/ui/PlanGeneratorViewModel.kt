package com.example.fitapp.feature.generator.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.generator.domain.EquipmentSetup
import com.example.fitapp.feature.generator.domain.FiveDaySplit
import com.example.fitapp.feature.generator.domain.GeneratedPlan
import com.example.fitapp.feature.generator.domain.PlanAnswers
import com.example.fitapp.feature.generator.domain.PlanGeneratorRepository
import com.example.fitapp.feature.profile.domain.FitnessLevel
import com.example.fitapp.feature.profile.domain.Goal
import com.example.fitapp.feature.profile.domain.ProfileRepository
import com.example.fitapp.feature.workout.domain.JointTag
import com.example.fitapp.feature.workout.domain.Muscle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ExplainState {
    data object Idle : ExplainState

    data object Loading : ExplainState

    data class Done(
        val text: String,
        val suggestions: List<String>,
        val leftToday: Int,
    ) : ExplainState

    data class Failed(val message: String) : ExplainState
}

data class GeneratorUiState(
    val answers: PlanAnswers = PlanAnswers(),
    /** True until the remembered answers (or the profile defaults) have been filled in. */
    val loadingAnswers: Boolean = true,
    val generating: Boolean = false,
    val plan: GeneratedPlan? = null,
    /** The answers the shown plan was made from. */
    val generatedFor: PlanAnswers? = null,
    val error: String? = null,
    val explain: ExplainState = ExplainState.Idle,
    val saving: Boolean = false,
    val replacePrevious: Boolean = true,
    /** How many routines the last save created; null until the user saves. */
    val savedRoutines: Int? = null,
    val message: String? = null,
) {
    /** The plan on screen no longer matches the answers: it should be generated again before saving or explaining. */
    val stale: Boolean get() = plan != null && generatedFor != answers

    val canGenerate: Boolean get() = !loadingAnswers && !generating
}

@HiltViewModel
class PlanGeneratorViewModel
    @Inject
    constructor(
        private val repository: PlanGeneratorRepository,
        private val profiles: ProfileRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(GeneratorUiState())
        val state: StateFlow<GeneratorUiState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                // Last time's answers if there are any, otherwise a sensible start from the profile.
                val remembered = repository.savedAnswers()
                val answers =
                    remembered
                        ?: profiles.observeProfile().first()?.let { p -> PlanAnswers(goal = p.goal, level = p.fitnessLevel) }
                        ?: PlanAnswers()
                _state.update { it.copy(answers = answers, loadingAnswers = false) }
            }
        }

        // ------------------------------------------------------------ the questionnaire

        fun setGoal(goal: Goal) = edit { it.copy(goal = goal) }

        fun setDays(days: Int) = edit { if (days in PlanAnswers.DAY_OPTIONS) it.copy(daysPerWeek = days) else it }

        fun setSessionMinutes(minutes: Int) = edit { if (minutes in PlanAnswers.SESSION_OPTIONS) it.copy(sessionMinutes = minutes) else it }

        fun setEquipment(equipment: EquipmentSetup) = edit { it.copy(equipment = equipment) }

        fun setLevel(level: FitnessLevel) = edit { it.copy(level = level) }

        fun setFiveDaySplit(split: FiveDaySplit) = edit { it.copy(fiveDaySplit = split) }

        fun toggleInjury(joint: JointTag) = edit { it.copy(injuries = it.injuries.toggled(joint)) }

        /** At most [PlanAnswers.MAX_PRIORITY] weak points: past that, picking another one is ignored with a hint. */
        fun togglePriority(muscle: Muscle) {
            val current = _state.value.answers.priorityMuscles
            if (muscle !in current && current.size >= PlanAnswers.MAX_PRIORITY) {
                _state.update { it.copy(message = "Pick up to ${PlanAnswers.MAX_PRIORITY} weak points.") }
                return
            }
            edit { it.copy(priorityMuscles = it.priorityMuscles.toggled(muscle)) }
        }

        fun setReplacePrevious(replace: Boolean) = _state.update { it.copy(replacePrevious = replace) }

        fun dismissMessage() = _state.update { it.copy(message = null) }

        // ------------------------------------------------------------ actions

        fun generate() {
            val s = _state.value
            if (!s.canGenerate) return
            val answers = s.answers
            _state.update { it.copy(generating = true, error = null, savedRoutines = null, explain = ExplainState.Idle) }
            viewModelScope.launch {
                when (val result = repository.generate(answers)) {
                    is AppResult.Success ->
                        _state.update { it.copy(generating = false, plan = result.value, generatedFor = answers) }
                    is AppResult.Failure -> _state.update { it.copy(generating = false, error = result.failure.message) }
                }
            }
        }

        fun explain() {
            val s = _state.value
            val answers = s.generatedFor ?: return
            if (s.plan == null || s.stale || s.explain == ExplainState.Loading) return
            _state.update { it.copy(explain = ExplainState.Loading) }
            viewModelScope.launch {
                when (val result = repository.explain(answers)) {
                    is AppResult.Success ->
                        _state.update {
                            it.copy(explain = ExplainState.Done(result.value.text, result.value.suggestions, result.value.explainsLeftToday))
                        }
                    is AppResult.Failure -> _state.update { it.copy(explain = ExplainState.Failed(result.failure.message)) }
                }
            }
        }

        fun save() {
            val s = _state.value
            val plan = s.plan ?: return
            if (s.stale || s.saving) return
            _state.update { it.copy(saving = true, error = null) }
            viewModelScope.launch {
                when (val result = repository.saveAsRoutines(plan, s.replacePrevious)) {
                    is AppResult.Success -> _state.update { it.copy(saving = false, savedRoutines = result.value) }
                    is AppResult.Failure -> _state.update { it.copy(saving = false, error = result.failure.message) }
                }
            }
        }

        // ------------------------------------------------------------ internals

        private fun edit(change: (PlanAnswers) -> PlanAnswers) {
            _state.update { it.copy(answers = change(it.answers), message = null, savedRoutines = null) }
        }

        private fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item
    }
