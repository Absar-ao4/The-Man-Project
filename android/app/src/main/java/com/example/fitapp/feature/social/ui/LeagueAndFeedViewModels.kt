package com.example.fitapp.feature.social.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.feature.profile.domain.DeviceInfo
import com.example.fitapp.feature.social.domain.FeedItem
import com.example.fitapp.feature.social.domain.IsoWeeks
import com.example.fitapp.feature.social.domain.Leaderboard
import com.example.fitapp.feature.social.domain.LeaderboardMetric
import com.example.fitapp.feature.social.domain.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LeagueUiState(
    val metric: LeaderboardMetric = LeaderboardMetric.XP,
    /** The week on screen, as an ISO week label. */
    val week: String,
    val currentWeek: String,
    val loading: Boolean = true,
    val leaderboard: Leaderboard? = null,
    val error: String? = null,
    /** True when the numbers on screen are the copy saved earlier because the network failed. */
    val savedCopy: Boolean = false,
) {
    /** Looking ahead of this week would show nothing. */
    val canGoNext: Boolean get() = week != currentWeek
}

@HiltViewModel
class LeagueViewModel
    @Inject
    constructor(
        private val repository: SocialRepository,
        device: DeviceInfo,
    ) : ViewModel() {
        private val today = device.today()
        private val currentWeek = IsoWeeks.of(today)
        private val _state = MutableStateFlow(LeagueUiState(week = currentWeek, currentWeek = currentWeek))
        val state: StateFlow<LeagueUiState> = _state.asStateFlow()

        val todayDate get() = today

        init {
            refresh()
        }

        fun setMetric(metric: LeaderboardMetric) {
            if (metric == _state.value.metric) return
            _state.update { it.copy(metric = metric) }
            refresh()
        }

        fun previousWeek() {
            IsoWeeks.previous(_state.value.week)?.let { week ->
                _state.update { it.copy(week = week) }
                refresh()
            }
        }

        fun nextWeek() {
            if (!_state.value.canGoNext) return
            IsoWeeks.next(_state.value.week)?.let { week ->
                _state.update { it.copy(week = week) }
                refresh()
            }
        }

        fun refresh() {
            val s = _state.value
            _state.update { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                // This week is asked for as "no week" so the server uses the viewer's own time zone for it.
                val load = repository.leaderboard(s.metric, s.week.takeIf { it != s.currentWeek })
                _state.update { latest ->
                    // The user may have moved on while this was loading: ignore an answer for something no longer shown.
                    if (latest.metric != s.metric || latest.week != s.week) {
                        latest
                    } else {
                        latest.copy(
                            loading = false,
                            leaderboard = load.data,
                            error = load.error?.message,
                            savedCopy = load.showingSavedCopy,
                        )
                    }
                }
            }
        }
    }

data class FeedUiState(
    val items: List<FeedItem> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val savedCopy: Boolean = false,
) {
    val isEmpty: Boolean get() = !loading && items.isEmpty() && error == null
}

@HiltViewModel
class FeedViewModel
    @Inject
    constructor(
        private val repository: SocialRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(FeedUiState())
        val state: StateFlow<FeedUiState> = _state.asStateFlow()

        init {
            refresh()
        }

        fun refresh() {
            _state.update { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                val load = repository.feed(null)
                _state.update {
                    it.copy(
                        loading = false,
                        items = load.data?.items.orEmpty(),
                        nextCursor = load.data?.nextCursor?.takeIf { _ -> !load.showingSavedCopy },
                        error = if (load.data == null) load.error?.message else null,
                        savedCopy = load.showingSavedCopy,
                    )
                }
            }
        }

        /** Older events, when the user reaches the end of the list. Not available from the saved copy. */
        fun loadMore() {
            val s = _state.value
            val cursor = s.nextCursor ?: return
            if (s.loadingMore || s.loading) return
            _state.update { it.copy(loadingMore = true) }
            viewModelScope.launch {
                val load = repository.feed(cursor)
                _state.update {
                    if (load.data == null) {
                        it.copy(loadingMore = false, error = load.error?.message)
                    } else {
                        it.copy(
                            loadingMore = false,
                            items = it.items + load.data.items.filter { new -> it.items.none { old -> old.id == new.id } },
                            nextCursor = load.data.nextCursor,
                            error = null,
                        )
                    }
                }
            }
        }
    }