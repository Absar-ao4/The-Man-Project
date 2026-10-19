package com.example.fitapp.feature.social.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.social.domain.Friend
import com.example.fitapp.feature.social.domain.FriendRequest
import com.example.fitapp.feature.social.domain.InviteCode
import com.example.fitapp.feature.social.domain.InviteLink
import com.example.fitapp.feature.social.domain.Privacy
import com.example.fitapp.feature.social.domain.Relation
import com.example.fitapp.feature.social.domain.SearchHit
import com.example.fitapp.feature.social.domain.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One line for the snackbar. */
sealed interface SocialEvent {
    data class Message(val text: String) : SocialEvent
}

// ====================================================================== friends and requests

data class FriendsUiState(
    val loading: Boolean = true,
    val friends: List<Friend> = emptyList(),
    val incoming: List<FriendRequest> = emptyList(),
    val outgoing: List<FriendRequest> = emptyList(),
    val error: String? = null,
    val savedCopy: Boolean = false,
    /** True while an action is running, so buttons are off and a second tap does nothing. */
    val busy: Boolean = false,
) {
    val isEmpty: Boolean get() = !loading && error == null && friends.isEmpty() && incoming.isEmpty() && outgoing.isEmpty()
}

@HiltViewModel
class FriendsViewModel
    @Inject
    constructor(
        private val repository: SocialRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(FriendsUiState())
        val state: StateFlow<FriendsUiState> = _state.asStateFlow()

        private val eventChannel = Channel<SocialEvent>(Channel.BUFFERED)
        val events: Flow<SocialEvent> = eventChannel.receiveAsFlow()

        init {
            refresh()
        }

        fun refresh() {
            _state.update { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                val friends = repository.friends()
                val requests = repository.requests()
                _state.update {
                    it.copy(
                        loading = false,
                        friends = friends.data.orEmpty(),
                        incoming = requests.data?.incoming.orEmpty(),
                        outgoing = requests.data?.outgoing.orEmpty(),
                        error = if (friends.data == null) friends.error?.message else null,
                        savedCopy = friends.showingSavedCopy || requests.showingSavedCopy,
                    )
                }
            }
        }

        fun accept(request: FriendRequest) = act("You are now friends with ${request.from.name}.") { repository.accept(request.id) }

        fun decline(request: FriendRequest) = act("Request declined.") { repository.decline(request.id) }

        fun cancel(request: FriendRequest) = act("Request cancelled.") { repository.cancel(request.id) }

        fun remove(friend: Friend) = act("${friend.user.name} is no longer your friend.") { repository.removeFriend(friend.user.id) }

        fun block(friend: Friend) = act("${friend.user.name} is blocked.") { repository.block(friend.user.id) }

        private fun act(
            success: String,
            call: suspend () -> AppResult<Unit>,
        ) {
            if (_state.value.busy) return
            _state.update { it.copy(busy = true) }
            viewModelScope.launch {
                when (val result = call()) {
                    is AppResult.Success -> {
                        eventChannel.send(SocialEvent.Message(success))
                        _state.update { it.copy(busy = false) }
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

// ====================================================================== finding friends

data class AddFriendUiState(
    val query: String = "",
    val searching: Boolean = false,
    val results: List<SearchHit> = emptyList(),
    val searchError: String? = null,
    val code: InviteCode? = null,
    val codeError: String? = null,
    val regenerating: Boolean = false,
    val codeInput: String = "",
    val redeeming: Boolean = false,
) {
    val canSearch: Boolean get() = query.trim().removePrefix("@").length >= MIN_QUERY

    companion object {
        const val MIN_QUERY = 2
    }
}

@HiltViewModel
class AddFriendViewModel
    @Inject
    constructor(
        private val repository: SocialRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(AddFriendUiState())
        val state: StateFlow<AddFriendUiState> = _state.asStateFlow()

        private val eventChannel = Channel<SocialEvent>(Channel.BUFFERED)
        val events: Flow<SocialEvent> = eventChannel.receiveAsFlow()

        private var searchJob: Job? = null

        init {
            loadCode()
        }

        // ------------------------------------------------------------ search

        /** Searches as the user types, after a short pause so every letter is not a request. */
        fun setQuery(text: String) {
            _state.update { it.copy(query = text.take(MAX_QUERY), searchError = null) }
            searchJob?.cancel()
            if (!_state.value.canSearch) {
                _state.update { it.copy(results = emptyList(), searching = false) }
                return
            }
            searchJob =
                viewModelScope.launch {
                    delay(SEARCH_PAUSE_MS)
                    runSearch()
                }
        }

        private suspend fun runSearch() {
            val query = _state.value.query
            _state.update { it.copy(searching = true) }
            when (val result = repository.search(query)) {
                is AppResult.Success ->
                    _state.update { if (it.query == query) it.copy(searching = false, results = result.value) else it }
                is AppResult.Failure ->
                    _state.update { if (it.query == query) it.copy(searching = false, results = emptyList(), searchError = result.failure.message) else it }
            }
        }

        fun sendRequest(hit: SearchHit) {
            viewModelScope.launch {
                when (val result = repository.sendRequest(username = null, userId = hit.user.id)) {
                    is AppResult.Success -> {
                        _state.update { s -> s.copy(results = s.results.map { if (it.user.id == hit.user.id) it.copy(relation = Relation.REQUEST_SENT) else it }) }
                        eventChannel.send(SocialEvent.Message("Request sent to ${hit.user.name}."))
                    }
                    is AppResult.Failure -> eventChannel.send(SocialEvent.Message(result.failure.message))
                }
            }
        }

        // ------------------------------------------------------------ my code

        private fun loadCode() {
            viewModelScope.launch {
                val load = repository.inviteCode()
                _state.update { it.copy(code = load.data, codeError = if (load.data == null) load.error?.message else null) }
            }
        }

        fun retryCode() = loadCode()

        /** A new code: the old one stops working at once. */
        fun regenerateCode() {
            if (_state.value.regenerating) return
            _state.update { it.copy(regenerating = true) }
            viewModelScope.launch {
                when (val result = repository.regenerateInviteCode()) {
                    is AppResult.Success -> {
                        _state.update { it.copy(regenerating = false, code = result.value, codeError = null) }
                        eventChannel.send(SocialEvent.Message("New code made. The old one no longer works."))
                    }
                    is AppResult.Failure -> {
                        _state.update { it.copy(regenerating = false) }
                        eventChannel.send(SocialEvent.Message(result.failure.message))
                    }
                }
            }
        }

        // ------------------------------------------------------------ using a friend's code

        fun setCodeInput(text: String) = _state.update { it.copy(codeInput = text.take(MAX_CODE_INPUT)) }

        /** Typed or pasted code. */
        fun redeemTyped() = redeem(_state.value.codeInput)

        /** Text from the QR scanner: a link or a bare code. */
        fun onScanned(text: String) = redeem(text)

        private fun redeem(text: String) {
            if (_state.value.redeeming) return
            val code = InviteLink.parse(text)
            if (code == null) {
                viewModelScope.launch { eventChannel.send(SocialEvent.Message("That does not look like an invite code.")) }
                return
            }
            _state.update { it.copy(redeeming = true) }
            viewModelScope.launch {
                when (val result = repository.redeem(code)) {
                    is AppResult.Success -> {
                        _state.update { it.copy(redeeming = false, codeInput = "") }
                        eventChannel.send(SocialEvent.Message("You are now friends with ${result.value.user.name}."))
                    }
                    is AppResult.Failure -> {
                        _state.update { it.copy(redeeming = false) }
                        eventChannel.send(SocialEvent.Message(result.failure.message))
                    }
                }
            }
        }

        companion object {
            const val SEARCH_PAUSE_MS = 400L
            private const val MAX_QUERY = 40
            private const val MAX_CODE_INPUT = 60
        }
    }

// ====================================================================== privacy and blocks

data class PrivacyUiState(
    val loading: Boolean = true,
    val privacy: Privacy = Privacy.DEFAULT,
    val blocked: List<Friend> = emptyList(),
    val error: String? = null,
    val saving: Boolean = false,
)

@HiltViewModel
class PrivacyViewModel
    @Inject
    constructor(
        private val repository: SocialRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(PrivacyUiState())
        val state: StateFlow<PrivacyUiState> = _state.asStateFlow()

        private val eventChannel = Channel<SocialEvent>(Channel.BUFFERED)
        val events: Flow<SocialEvent> = eventChannel.receiveAsFlow()

        init {
            load()
        }

        fun load() {
            _state.update { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                val privacy = repository.privacy()
                val blocked = repository.blocked()
                _state.update {
                    it.copy(
                        loading = false,
                        privacy = privacy.data ?: it.privacy,
                        blocked = blocked.data.orEmpty(),
                        error = if (privacy.data == null) privacy.error?.message else null,
                    )
                }
            }
        }

        fun setShowFeed(on: Boolean) = change { it.copy(showFeed = on) }

        fun setShowPrWeights(on: Boolean) = change { it.copy(showPrWeights = on) }

        fun setShowOnLeaderboard(on: Boolean) = change { it.copy(showOnLeaderboard = on) }

        /** Shows the new setting at once and saves it; if saving fails the old setting comes back with a message. */
        private fun change(edit: (Privacy) -> Privacy) {
            val before = _state.value.privacy
            val after = edit(before)
            if (after == before) return
            _state.update { it.copy(privacy = after, saving = true) }
            viewModelScope.launch {
                when (val result = repository.updatePrivacy(after)) {
                    is AppResult.Success -> _state.update { it.copy(saving = false, privacy = result.value) }
                    is AppResult.Failure -> {
                        _state.update { it.copy(saving = false, privacy = before) }
                        eventChannel.send(SocialEvent.Message(result.failure.message))
                    }
                }
            }
        }

        fun unblock(friend: Friend) {
            viewModelScope.launch {
                when (val result = repository.unblock(friend.user.id)) {
                    is AppResult.Success -> {
                        _state.update { s -> s.copy(blocked = s.blocked.filterNot { it.user.id == friend.user.id }) }
                        eventChannel.send(SocialEvent.Message("${friend.user.name} is unblocked."))
                    }
                    is AppResult.Failure -> eventChannel.send(SocialEvent.Message(result.failure.message))
                }
            }
        }
    }