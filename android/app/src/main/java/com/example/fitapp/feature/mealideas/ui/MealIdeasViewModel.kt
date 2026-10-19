package com.example.fitapp.feature.mealideas.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.diet.domain.Allergen
import com.example.fitapp.feature.diet.domain.DietType
import com.example.fitapp.feature.mealideas.domain.IdeaRequest
import com.example.fitapp.feature.mealideas.domain.MealIdea
import com.example.fitapp.feature.mealideas.domain.MealIdeaRepository
import com.example.fitapp.feature.mealideas.domain.MealIdeas
import com.example.fitapp.feature.nutrition.domain.LoggedItem
import com.example.fitapp.feature.nutrition.domain.MealRepository
import com.example.fitapp.feature.nutrition.domain.MealSource
import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.profile.domain.DeviceInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface IdeasResult {
    /** Nothing asked for yet: the screen shows the choices and the button. */
    data object Idle : IdeasResult

    data object Loading : IdeasResult

    data class Content(val ideas: MealIdeas) : IdeasResult

    data class Error(val message: String) : IdeasResult
}

data class MealIdeasUiState(
    val mealType: MealType? = null,
    val dietType: DietType = DietType.NON_VEGETARIAN,
    val allergies: Set<Allergen> = emptySet(),
    val result: IdeasResult = IdeasResult.Idle,
    /** Indexes of the ideas already logged from the current answer. */
    val logged: Set<Int> = emptySet(),
    val logging: Boolean = false,
)

sealed interface MealIdeasEvent {
    data class Message(val text: String) : MealIdeasEvent
}

interface MealIdeasActions {
    fun onMealType(type: MealType?)

    fun onDietType(type: DietType)

    fun onAllergy(allergen: Allergen)

    fun onGetIdeas()

    fun onLog(index: Int)
}

@HiltViewModel
class MealIdeasViewModel
    @Inject
    constructor(
        private val repository: MealIdeaRepository,
        private val meals: MealRepository,
        private val device: DeviceInfo,
    ) : ViewModel(), MealIdeasActions {
        private val _state = MutableStateFlow(MealIdeasUiState(mealType = MealType.forHour(device.currentHour())))
        val state: StateFlow<MealIdeasUiState> = _state.asStateFlow()

        private val eventChannel = Channel<MealIdeasEvent>(Channel.BUFFERED)
        val events = eventChannel.receiveAsFlow()

        override fun onMealType(type: MealType?) {
            _state.update { it.copy(mealType = type) }
        }

        override fun onDietType(type: DietType) {
            _state.update { it.copy(dietType = type) }
        }

        override fun onAllergy(allergen: Allergen) {
            _state.update { it.copy(allergies = if (allergen in it.allergies) it.allergies - allergen else it.allergies + allergen) }
        }

        override fun onGetIdeas() {
            val s = _state.value
            if (s.result is IdeasResult.Loading) return
            _state.update { it.copy(result = IdeasResult.Loading, logged = emptySet()) }
            viewModelScope.launch {
                val result = repository.ideas(IdeaRequest(s.mealType, s.dietType, s.allergies))
                _state.update {
                    it.copy(
                        result =
                            when (result) {
                                is AppResult.Success -> IdeasResult.Content(result.value)
                                is AppResult.Failure -> IdeasResult.Error(result.failure.message)
                            },
                    )
                }
            }
        }

        /** Logs one idea to today's food log, written to the device first like every other entry, then synced. */
        override fun onLog(index: Int) {
            val s = _state.value
            val content = s.result as? IdeasResult.Content ?: return
            val idea: MealIdea = content.ideas.ideas.getOrNull(index) ?: return
            if (s.logging) return
            _state.update { it.copy(logging = true) }
            viewModelScope.launch {
                val type = s.mealType ?: MealType.forHour(device.currentHour())
                val items =
                    idea.items.map { item ->
                        LoggedItem(name = item.name, foodRef = null, grams = item.grams, macros = item.macros, source = MealSource.SUGGESTION)
                    }
                when (val result = meals.addItems(device.today(), type, items)) {
                    is AppResult.Success -> {
                        _state.update { it.copy(logging = false, logged = it.logged + index) }
                        eventChannel.send(MealIdeasEvent.Message("Logged ${idea.title} (${Math.round(idea.totals.calories)} kcal) to ${type.label.lowercase()}."))
                    }
                    is AppResult.Failure -> {
                        _state.update { it.copy(logging = false) }
                        eventChannel.send(MealIdeasEvent.Message(result.failure.message))
                    }
                }
            }
        }
    }