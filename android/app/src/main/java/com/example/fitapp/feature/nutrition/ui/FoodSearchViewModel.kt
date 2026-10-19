package com.example.fitapp.feature.nutrition.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.nutrition.domain.Food
import com.example.fitapp.feature.nutrition.domain.FoodRepository
import com.example.fitapp.feature.nutrition.domain.SearchOrigin
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchTab(val label: String) {
    SEARCH("Search"),
    RECENT("Recent"),
    FAVORITES("Favorites"),
    MINE("My foods"),
}

sealed interface SearchResultsState {
    /** Nothing typed yet. */
    data object Idle : SearchResultsState

    data object Loading : SearchResultsState

    data class Results(val items: List<Food>, val origin: SearchOrigin) : SearchResultsState

    data class Empty(val query: String) : SearchResultsState

    data class Error(val message: String) : SearchResultsState
}

data class FoodSearchUiState(
    val tab: SearchTab = SearchTab.SEARCH,
    val query: String = "",
    val results: SearchResultsState = SearchResultsState.Idle,
    val recents: List<Food> = emptyList(),
    val favorites: List<Food> = emptyList(),
    val mine: List<Food> = emptyList(),
    val favoriteRefs: Set<String> = emptySet(),
    val barcodeBusy: Boolean = false,
    val message: String? = null,
)

sealed interface FoodSearchEvent {
    /** A food was chosen: open the portion screen for it. */
    data class OpenPortion(val foodRef: String, val fromBarcode: Boolean) : FoodSearchEvent
}

@HiltViewModel
@OptIn(FlowPreview::class)
class FoodSearchViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val foods: FoodRepository,
    ) : ViewModel() {
        private val tab = MutableStateFlow(SearchTab.SEARCH)
        private val query = MutableStateFlow("")
        private val results = MutableStateFlow<SearchResultsState>(SearchResultsState.Idle)
        private val barcodeBusy = MutableStateFlow(false)
        private val message = MutableStateFlow<String?>(null)

        private val eventChannel = Channel<FoodSearchEvent>(Channel.BUFFERED)
        val events: Flow<FoodSearchEvent> = eventChannel.receiveAsFlow()

        val state: StateFlow<FoodSearchUiState> =
            combine(
                combine(tab, query, results, barcodeBusy, message) { t, q, r, b, m -> FoodSearchUiState(t, q, r, barcodeBusy = b, message = m) },
                foods.observeRecents(),
                foods.observeFavorites(),
                foods.observeCustomFoods(),
            ) { base, recents, favorites, mine ->
                base.copy(
                    recents = recents,
                    favorites = favorites,
                    mine = mine,
                    favoriteRefs = favorites.map { it.foodRef }.toSet(),
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FoodSearchUiState())

        init {
            // Typing searches after a short pause so each keystroke does not hit the server.
            viewModelScope.launch {
                query
                    .debounce(DEBOUNCE_MS)
                    .distinctUntilChanged()
                    .collect { runSearch(it) }
            }
            viewModelScope.launch { foods.refresh() }
        }

        fun onTabChange(value: SearchTab) = tab.update { value }

        fun onQueryChange(value: String) {
            query.value = value
            if (value.trim().length < MIN_QUERY) results.value = SearchResultsState.Idle
        }

        fun retry() {
            viewModelScope.launch { runSearch(query.value) }
        }

        fun select(food: Food) {
            viewModelScope.launch {
                foods.remember(food)
                eventChannel.send(FoodSearchEvent.OpenPortion(food.foodRef, fromBarcode = false))
            }
        }

        fun toggleFavorite(food: Food) {
            viewModelScope.launch { foods.toggleFavorite(food) }
        }

        fun deleteCustomFood(food: Food) {
            viewModelScope.launch { foods.deleteCustomFood(food.foodRef) }
        }

        fun onBarcode(code: String) {
            viewModelScope.launch {
                barcodeBusy.value = true
                message.value = null
                when (val result = foods.lookupBarcode(code)) {
                    is AppResult.Success -> {
                        foods.remember(result.value)
                        eventChannel.send(FoodSearchEvent.OpenPortion(result.value.foodRef, fromBarcode = true))
                    }
                    is AppResult.Failure ->
                        message.value =
                            if (result.failure is com.example.fitapp.core.network.ApiFailure.NotFound) {
                                "No product found for barcode $code. You can add it as your own food."
                            } else {
                                result.failure.message
                            }
                }
                barcodeBusy.value = false
            }
        }

        fun dismissMessage() {
            message.value = null
        }

        private suspend fun runSearch(raw: String) {
            val text = raw.trim()
            if (text.length < MIN_QUERY) {
                results.value = SearchResultsState.Idle
                return
            }
            results.value = SearchResultsState.Loading
            results.value =
                when (val result = foods.search(text)) {
                    is AppResult.Success ->
                        if (result.value.items.isEmpty()) {
                            SearchResultsState.Empty(text)
                        } else {
                            SearchResultsState.Results(result.value.items, result.value.origin)
                        }
                    is AppResult.Failure -> SearchResultsState.Error(result.failure.message)
                }
        }

        val args: SearchArgs = SearchArgs.from(savedStateHandle)

        private companion object {
            const val DEBOUNCE_MS = 400L
            const val MIN_QUERY = 2
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

/** Where a chosen food will be logged; carried from the log screen through search to the portion screen. */
data class SearchArgs(
    val date: String,
    val meal: String,
) {
    companion object {
        fun from(handle: SavedStateHandle) = SearchArgs(date = handle["date"] ?: "", meal = handle["meal"] ?: "")
    }
}
