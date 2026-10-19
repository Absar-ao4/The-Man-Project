package com.example.fitapp.feature.social.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.components.EmptyState
import com.example.fitapp.core.designsystem.components.ErrorState
import com.example.fitapp.core.designsystem.components.LoadingState
import com.example.fitapp.core.designsystem.components.ManButton
import com.example.fitapp.core.designsystem.components.ManCard
import com.example.fitapp.core.designsystem.components.ManChip
import com.example.fitapp.core.designsystem.components.ManOutlinedButton
import com.example.fitapp.core.designsystem.components.ManTextButton
import com.example.fitapp.core.designsystem.components.SectionTitle
import com.example.fitapp.feature.social.domain.Duel
import com.example.fitapp.feature.social.domain.DuelMetric
import com.example.fitapp.feature.social.domain.DuelStatus
import com.example.fitapp.feature.social.domain.DuelText
import com.example.fitapp.feature.social.domain.Friend
import com.example.fitapp.feature.social.domain.FriendRequest

// ====================================================================== friends

class FriendsActions(
    val onRetry: () -> Unit,
    val onAddFriend: () -> Unit,
    val onAccept: (FriendRequest) -> Unit,
    val onDecline: (FriendRequest) -> Unit,
    val onCancel: (FriendRequest) -> Unit,
    val onRemove: (Friend) -> Unit,
    val onBlock: (Friend) -> Unit,
)

@Composable
internal fun FriendsTab(
    onAddFriend: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val viewModel: FriendsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SocialEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }
    FriendsContent(
        state,
        FriendsActions(
            onRetry = viewModel::refresh,
            onAddFriend = onAddFriend,
            onAccept = viewModel::accept,
            onDecline = viewModel::decline,
            onCancel = viewModel::cancel,
            onRemove = viewModel::remove,
            onBlock = viewModel::block,
        ),
    )
}

@Composable
fun FriendsContent(
    state: FriendsUiState,
    actions: FriendsActions,
) {
    var managing by remember { mutableStateOf<Friend?>(null) }
    when {
        state.loading && state.friends.isEmpty() && state.incoming.isEmpty() -> LoadingState()
        state.error != null && state.friends.isEmpty() && state.incoming.isEmpty() -> ErrorState(message = state.error, onRetry = actions.onRetry)
        state.isEmpty ->
            EmptyState(
                title = "No friends yet",
                message = "Find people by username, or share your invite code or QR code.",
                actionLabel = "Add a friend",
                onAction = actions.onAddFriend,
            )
        else ->
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("friends"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.savedCopy) {
                    Text(
                        "Showing what was saved earlier. You seem to be offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ManTheme.colors.muted,
                        modifier = Modifier.testTag("saved_copy"),
                    )
                }
                if (state.incoming.isNotEmpty()) {
                    SectionTitle("Requests for you")
                    state.incoming.forEach { request ->
                        ManCard(modifier = Modifier.fillMaxWidth().testTag("request_in_${request.id}")) {
                            Text(request.from.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "@${request.from.username} · Level ${request.from.level}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ManTheme.colors.muted,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                ManButton(
                                    text = "Accept",
                                    onClick = { actions.onAccept(request) },
                                    enabled = !state.busy,
                                    modifier = Modifier.weight(1f).testTag("accept_${request.id}"),
                                )
                                ManOutlinedButton(
                                    text = "Decline",
                                    onClick = { actions.onDecline(request) },
                                    enabled = !state.busy,
                                    modifier = Modifier.weight(1f).testTag("decline_${request.id}"),
                                )
                            }
                        }
                    }
                }
                if (state.outgoing.isNotEmpty()) {
                    SectionTitle("Waiting for an answer")
                    state.outgoing.forEach { request ->
                        ManCard(modifier = Modifier.fillMaxWidth().testTag("request_out_${request.id}")) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(request.to.name, style = MaterialTheme.typography.titleMedium)
                                    Text("@${request.to.username}", style = MaterialTheme.typography.bodySmall, color = ManTheme.colors.muted)
                                }
                                ManTextButton(
                                    text = "Cancel",
                                    onClick = { actions.onCancel(request) },
                                    enabled = !state.busy,
                                    modifier = Modifier.testTag("cancel_${request.id}"),
                                )
                            }
                        }
                    }
                }
                SectionTitle("Friends (${state.friends.size})")
                if (state.friends.isEmpty()) {
                    Text("No friends yet.", style = MaterialTheme.typography.bodyMedium, color = ManTheme.colors.muted)
                }
                state.friends.forEach { friend ->
                    ManCard(onClick = { managing = friend }, modifier = Modifier.fillMaxWidth().testTag("friend_${friend.user.id}")) {
                        Text(friend.user.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "@${friend.user.username} · Level ${friend.user.level} · ${friend.user.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ManTheme.colors.muted,
                        )
                    }
                }
                ManOutlinedButton(text = "Add a friend", onClick = actions.onAddFriend, modifier = Modifier.fillMaxWidth().testTag("friends_add"))
            }
    }
    managing?.let { friend ->
        AlertDialog(
            onDismissRequest = { managing = null },
            title = { Text(friend.user.name) },
            text = { Text("Removing a friend ends your duels with them. Blocking also hides you from each other everywhere.") },
            confirmButton = {
                ManTextButton(
                    text = "Remove friend",
                    onClick = {
                        managing = null
                        actions.onRemove(friend)
                    },
                    modifier = Modifier.testTag("friend_remove"),
                )
            },
            dismissButton = {
                Row {
                    ManTextButton(
                        text = "Block",
                        onClick = {
                            managing = null
                            actions.onBlock(friend)
                        },
                        modifier = Modifier.testTag("friend_block"),
                    )
                    ManTextButton(text = "Close", onClick = { managing = null })
                }
            },
        )
    }
}

// ====================================================================== duels

class DuelsActions(
    val onRetry: () -> Unit,
    val onNew: () -> Unit,
    val onAccept: (Duel) -> Unit,
    val onDecline: (Duel) -> Unit,
    val onWithdraw: (Duel) -> Unit,
    val onChooseOpponent: (String) -> Unit,
    val onChooseMetric: (DuelMetric) -> Unit,
    val onSend: () -> Unit,
    val onCancelNew: () -> Unit,
)

@Composable
internal fun DuelsTab(snackbar: SnackbarHostState) {
    val viewModel: DuelsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SocialEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }
    DuelsContent(
        state,
        DuelsActions(
            onRetry = viewModel::refresh,
            onNew = viewModel::startCreating,
            onAccept = viewModel::accept,
            onDecline = viewModel::decline,
            onWithdraw = viewModel::withdraw,
            onChooseOpponent = viewModel::chooseOpponent,
            onChooseMetric = viewModel::chooseMetric,
            onSend = viewModel::sendChallenge,
            onCancelNew = viewModel::cancelCreating,
        ),
    )
}

@Composable
fun DuelsContent(
    state: DuelsUiState,
    actions: DuelsActions,
) {
    when {
        state.loading && state.duels.isEmpty() -> LoadingState()
        state.error != null && state.duels.isEmpty() -> ErrorState(message = state.error, onRetry = actions.onRetry)
        else ->
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("duels"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.savedCopy) {
                    Text(
                        "Showing what was saved earlier. You seem to be offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ManTheme.colors.muted,
                        modifier = Modifier.testTag("saved_copy"),
                    )
                }
                ManButton(text = "Challenge a friend", onClick = actions.onNew, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("duel_new"))
                if (state.isEmpty) {
                    EmptyState(
                        title = "No duels",
                        message = "Challenge a friend to a 7 day duel: most workouts, most XP or most Lock-in sessions. The winner gets 100 XP and a badge.",
                    )
                }
                if (state.waitingForMe.isNotEmpty()) {
                    SectionTitle("Challenges for you")
                    state.waitingForMe.forEach { duel -> DuelCard(duel, state.busy, actions) }
                }
                if (state.active.isNotEmpty()) {
                    SectionTitle("Running")
                    state.active.forEach { duel -> DuelCard(duel, state.busy, actions) }
                }
                if (state.sent.isNotEmpty()) {
                    SectionTitle("Waiting for an answer")
                    state.sent.forEach { duel -> DuelCard(duel, state.busy, actions) }
                }
                if (state.past.isNotEmpty()) {
                    SectionTitle("Past duels")
                    state.past.forEach { duel -> DuelCard(duel, state.busy, actions) }
                }
            }
    }
    state.creating?.let { draft -> NewDuelDialog(state, draft, actions) }
}

@Composable
private fun DuelCard(
    duel: Duel,
    busy: Boolean,
    actions: DuelsActions,
) {
    ManCard(modifier = Modifier.fillMaxWidth().testTag("duel_${duel.id}")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("You vs ${duel.other.name}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            Text(duel.metric.label, style = MaterialTheme.typography.labelMedium, color = ManTheme.colors.muted)
        }
        Text(DuelText.status(duel), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("duel_status_${duel.id}"))
        if (duel.status == DuelStatus.ACTIVE || duel.status == DuelStatus.FINISHED) {
            val total = (duel.myScore + duel.theirScore).coerceAtLeast(1)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("You ${duel.myScore}", style = MaterialTheme.typography.titleSmall)
                Text("${duel.other.name} ${duel.theirScore}", style = MaterialTheme.typography.titleSmall)
            }
            LinearProgressIndicator(
                progress = { duel.myScore.toFloat() / total },
                modifier = Modifier.fillMaxWidth().testTag("duel_bar_${duel.id}"),
                color = ManTheme.colors.accent,
                trackColor = ManTheme.colors.hairline,
            )
            if (duel.status == DuelStatus.ACTIVE) {
                duel.endsAt?.let { Text("Ends ${it.take(10)}", style = MaterialTheme.typography.bodySmall, color = ManTheme.colors.muted) }
            }
        }
        if (duel.needsMyAnswer) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ManButton(text = "Accept", onClick = { actions.onAccept(duel) }, enabled = !busy, modifier = Modifier.weight(1f).testTag("duel_accept_${duel.id}"))
                ManOutlinedButton(text = "Decline", onClick = { actions.onDecline(duel) }, enabled = !busy, modifier = Modifier.weight(1f).testTag("duel_decline_${duel.id}"))
            }
        }
        if (duel.canWithdraw) {
            ManTextButton(text = "Withdraw", onClick = { actions.onWithdraw(duel) }, enabled = !busy, modifier = Modifier.testTag("duel_withdraw_${duel.id}"))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewDuelDialog(
    state: DuelsUiState,
    draft: NewDuel,
    actions: DuelsActions,
) {
    AlertDialog(
        onDismissRequest = actions.onCancelNew,
        title = { Text("Challenge a friend") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (state.friends.isEmpty()) {
                    Text("You need a friend first. Add one from the Friends tab.", style = MaterialTheme.typography.bodyMedium)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.friends.forEach { friend ->
                        ManChip(
                            label = friend.user.name,
                            selected = draft.opponentId == friend.user.id,
                            onClick = { actions.onChooseOpponent(friend.user.id) },
                            modifier = Modifier.testTag("duel_opponent_${friend.user.id}"),
                        )
                    }
                }
                Text("What counts", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DuelMetric.entries.forEach { metric ->
                        ManChip(
                            label = metric.label,
                            selected = draft.metric == metric,
                            onClick = { actions.onChooseMetric(metric) },
                            modifier = Modifier.testTag("duel_metric_${metric.name}"),
                        )
                    }
                }
                Text("The duel starts when they accept and runs for 7 days.", style = MaterialTheme.typography.bodySmall, color = ManTheme.colors.muted)
            }
        },
        confirmButton = {
            ManTextButton(text = "Send challenge", enabled = draft.ready && !state.busy, onClick = actions.onSend, modifier = Modifier.testTag("duel_send"))
        },
        dismissButton = { ManTextButton(text = "Cancel", onClick = actions.onCancelNew) },
    )
}
