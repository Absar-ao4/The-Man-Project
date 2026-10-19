package com.example.fitapp.feature.diet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.diet.domain.CheapestProtein
import com.example.fitapp.feature.diet.domain.DietFood
import com.example.fitapp.feature.diet.domain.DietRepository
import com.example.fitapp.feature.diet.domain.DietType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CheapestProteinUiState {
    data object Loading : CheapestProteinUiState

    data class Error(val message: String) : CheapestProteinUiState

    data class Content(
        val currency: String,
        val foods: List<CheapestProtein>,
        /** Null means every diet. */
        val dietType: DietType?,
    ) : CheapestProteinUiState
}

@HiltViewModel
class CheapestProteinViewModel
    @Inject
    constructor(
        private val repository: DietRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow<CheapestProteinUiState>(CheapestProteinUiState.Loading)
        val state: StateFlow<CheapestProteinUiState> = _state.asStateFlow()

        private var dietType: DietType? = null

        init {
            load()
        }

        fun setDietType(type: DietType?) {
            if (type == dietType && _state.value is CheapestProteinUiState.Content) return
            dietType = type
            load()
        }

        fun load() {
            val requested = dietType
            _state.value = CheapestProteinUiState.Loading
            viewModelScope.launch {
                _state.value =
                    when (val result = repository.cheapestProtein(requested, null)) {
                        is AppResult.Success -> CheapestProteinUiState.Content(result.value.currency, result.value.foods, requested)
                        is AppResult.Failure -> CheapestProteinUiState.Error(result.failure.message)
                    }
            }
        }
    }

sealed interface DietPricesUiState {
    data object Loading : DietPricesUiState

    data class Error(val message: String) : DietPricesUiState

    data class Content(
        val currency: String,
        val foods: List<DietFood>,
        val dietType: DietType?,
        /** Set while a price is being edited. */
        val editing: DietFood? = null,
        val saving: Boolean = false,
        val editError: String? = null,
    ) : DietPricesUiState
}

@HiltViewModel
class DietPricesViewModel
    @Inject
    constructor(
        private val repository: DietRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow<DietPricesUiState>(DietPricesUiState.Loading)
        val state: StateFlow<DietPricesUiState> = _state.asStateFlow()

        private var dietType: DietType? = null

        init {
            load()
        }

        fun load() {
            val requested = dietType
            _state.value = DietPricesUiState.Loading
            viewModelScope.launch {
                _state.value =
                    when (val result = repository.foods(requested, null)) {
                        is AppResult.Success -> DietPricesUiState.Content(result.value.currency, result.value.foods, requested)
                        is AppResult.Failure -> DietPricesUiState.Error(result.failure.message)
                    }
            }
        }

        fun setDietType(type: DietType?) {
            if (type == dietType && _state.value is DietPricesUiState.Content) return
            dietType = type
            load()
        }

        fun startEdit(food: DietFood) = editContent { it.copy(editing = food, editError = null) }

        fun cancelEdit() = editContent { it.copy(editing = null, editError = null) }

        /** [text] is what the user typed: a price per the food's unit. */
        fun savePrice(text: String) {
            val content = _state.value as? DietPricesUiState.Content ?: return
            val food = content.editing ?: return
            val price = text.replace(',', '.').trim().toDoubleOrNull()
            if (price == null || !(price > 0.0)) {
                _state.value = content.copy(editError = "Enter a price above 0.")
                return
            }
            _state.value = content.copy(saving = true, editError = null)
            viewModelScope.launch { applyResult(food, repository.setPrice(food.id, price)) }
        }

        fun resetPrice(food: DietFood) {
            val content = _state.value as? DietPricesUiState.Content ?: return
            _state.value = content.copy(saving = true, editError = null)
            viewModelScope.launch { applyResult(food, repository.resetPrice(food.id)) }
        }

        private fun applyResult(
            old: DietFood,
            result: AppResult<DietFood>,
        ) {
            _state.update { s ->
                val content = s as? DietPricesUiState.Content ?: return@update s
                when (result) {
                    is AppResult.Success ->
                        content.copy(
                            foods = content.foods.map { if (it.id == old.id) result.value else it },
                            editing = null,
                            saving = false,
                            editError = null,
                        )
                    is AppResult.Failure -> content.copy(saving = false, editError = result.failure.message)
                }
            }
        }

        private fun editContent(change: (DietPricesUiState.Content) -> DietPricesUiState.Content) {
            _state.update { s -> (s as? DietPricesUiState.Content)?.let(change) ?: s }
        }
    }