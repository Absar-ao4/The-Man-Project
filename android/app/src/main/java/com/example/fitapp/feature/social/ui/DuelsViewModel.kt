package com.example.fitapp.feature.social.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.social.domain.Duel
import com.example.fitapp.feature.social.domain.DuelMetric
import com.example.fitapp.feature.social.domain.DuelStatus
import com.example.fitapp.feature.social.domain.Friend
import com.example.fitapp.feature.social.domain.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DuelsUiState(
    val loading: Boolean = true,
    val duels: List<Duel> = emptyList(),
    val friends: List<Friend> = emptyList(),
    val error: String? = null,
    val savedCopy: Boolean = false,
    val busy: Boolean = false,
    /** Set while the "new duel" dialog is open. */
    val creating: NewDuel? = null,
) {
    val waitingForMe: List<Duel> get() = duels.filter { it.needsMyAnswer }
    val active: List<Duel> get() = duels.filter { it.status == DuelStatus.ACTIVE }
    val sent: List<Duel> get() = duels.filter { it.canWithdraw }
    val past: List<Duel> get() = duels.filter { it.status != DuelStatus.ACTIVE && it.status != DuelStatus.PENDING }
    val isEmpty: Boolean get() = !loading && error == null && duels.isEmpty()
}

/** The choices in the "new duel" dialog. */
data class NewDuel(
    val opponentId: String? = null,
    val metric: DuelMetric = DuelMetric.WORKOUTS,
) {
    val ready: Boolean get() = opponentId != null
}

@HiltViewModel
class DuelsViewModel
    @Inject
    constructor(
        private val repository: SocialRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(DuelsUiState())
        val state: StateFlow<DuelsUiState> = _state.asStateFlow()

        private val eventChannel = Channel<SocialEvent>(Channel.BUFFERED)
        val events: Flow<SocialEvent> = eventChannel.receiveAsFlow()

        init {
            refresh()
        }

        fun refresh() {
            _state.update { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                val duels = repository.duels()
                val friends = repository.friends()
                _state.update {
                    it.copy(
                        loading = false,
                        duels = duels.data.orEmpty(),
                        friends = friends.data.orEmpty(),
                        error = if (duels.data == null) duels.error?.message else null,
                        savedCopy = duels.showingSavedCopy,
                    )
                }
            }
        }

        // ------------------------------------------------------------ the new duel dialog

        fun startCreating() = _state.update { it.copy(creating = NewDuel()) }

        fun cancelCreating() = _state.update { it.copy(creating = null) }

        fun chooseOpponent(friendId: String) = _state.update { s -> s.copy(creating = s.creating?.copy(opponentId = friendId)) }

        fun chooseMetric(metric: DuelMetric) = _state.update { s -> s.copy(creating = s.creating?.copy(metric = metric)) }

        fun sendChallenge() {
            val s = _state.value
            val draft = s.creating ?: return
            val opponent = draft.opponentId ?: return
            if (s.busy) return
            _state.update { it.copy(busy = true) }
            viewModelScope.launch {
                when (val result = repository.createDuel(opponent, draft.metric)) {
                    is AppResult.Success -> {
                        _state.update { it.copy(busy = false, creating = null) }
                        eventChannel.send(SocialEvent.Message("Challenge sent to ${result.value.other.name}."))
                        refresh()
                    }
                    is AppResult.Failure -> {
                        _state.update { it.copy(busy = false) }
                        eventChannel.send(SocialEvent.Message(result.failure.message))
                    }
                }
            }
        }

        // ------------------------------------------------------------ answering

        fun accept(duel: Duel) = act("Duel on! It runs for 7 days.") { repository.acceptDuel(duel.id) }

        fun decline(duel: Duel) = act("Challenge declined.") { repository.declineDuel(duel.id) }

        fun withdraw(duel: Duel) = act("Challenge withdrawn.") { repository.cancelDuel(duel.id) }

        private fun act(
            success: String,
            call: suspend () -> AppResult<Duel>,
        ) {
            if (_state.value.busy) return
            _state.update { it.copy(busy = true) }
            viewModelScope.launch {
                when (val result = call()) {
                    is AppResult.Success -> {
                        _state.update { it.copy(busy = false) }
                        eventChannel.send(SocialEvent.Message(success))
                        refresh()
                    }
                    is AppResult.Failure -> {
                        _state.update { it.copy(busy = false) }
                        eventChannel.send(SocialEvent.Message(result.failure.message))
                    }
                }
            }
        }
    }