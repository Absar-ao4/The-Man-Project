package com.example.fitapp.feature.social.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.components.ErrorState
import com.example.fitapp.core.designsystem.components.LoadingState
import com.example.fitapp.core.designsystem.components.ManButton
import com.example.fitapp.core.designsystem.components.ManCard
import com.example.fitapp.core.designsystem.components.ManOutlinedButton
import com.example.fitapp.core.designsystem.components.ManTextButton
import com.example.fitapp.core.designsystem.components.ManTextField
import com.example.fitapp.core.designsystem.components.SectionTitle
import com.example.fitapp.feature.social.domain.Friend
import com.example.fitapp.feature.social.domain.InviteLink
import com.example.fitapp.feature.social.domain.Relation
import com.example.fitapp.feature.social.domain.SearchHit
import com.example.fitapp.feature.workout.ui.WorkoutScaffold
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import com.example.fitapp.core.designsystem.ManShapes

// ====================================================================== add a friend

class AddFriendActions(
    val onQuery: (String) -> Unit,
    val onSend: (SearchHit) -> Unit,
    val onCodeInput: (String) -> Unit,
    val onRedeem: () -> Unit,
    val onScan: () -> Unit,
    val onShare: (String) -> Unit,
    val onRegenerate: () -> Unit,
    val onRetryCode: () -> Unit,
)

@Composable
fun AddFriendScreen(
    onBack: () -> Unit,
    viewModel: AddFriendViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SocialEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }
    val scan =
        rememberQrScanner(
            onText = viewModel::onScanned,
            onUnavailable = { scope.launch { snackbar.showSnackbar("The QR scanner is not available here. Type the code instead.") } },
        )
    WorkoutScaffold(title = "Add a friend", onBack = onBack, snackbarHostState = snackbar) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            AddFriendContent(
                state = state,
                actions =
                    AddFriendActions(
                        onQuery = viewModel::setQuery,
                        onSend = viewModel::sendRequest,
                        onCodeInput = viewModel::setCodeInput,
                        onRedeem = viewModel::redeemTyped,
                        onScan = scan,
                        onShare = { code ->
                            val send =
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, InviteLink.shareText(code))
                                }
                            context.startActivity(Intent.createChooser(send, "Share your invite code"))
                        },
                        onRegenerate = viewModel::regenerateCode,
                        onRetryCode = viewModel::retryCode,
                    ),
            )
        }
    }
}

@Composable
fun AddFriendContent(
    state: AddFriendUiState,
    actions: AddFriendActions,
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp).testTag("add_friend"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle("Find by username")
        ManTextField(
            value = state.query,
            onValueChange = actions.onQuery,
            label = "Username",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth().testTag("friend_search"),
        )
        when {
            state.searching -> Text("Searching...", style = MaterialTheme.typography.bodySmall, color = ManTheme.colors.muted)
            state.searchError != null -> Text(state.searchError, color = ManTheme.colors.danger, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("search_error"))
            !state.canSearch && state.query.isNotEmpty() ->
                Text("Type at least ${AddFriendUiState.MIN_QUERY} characters.", style = MaterialTheme.typography.bodySmall, color = ManTheme.colors.muted)
            state.canSearch && state.results.isEmpty() ->
                Text("Nobody found. Check the spelling, or use a code below.", style = MaterialTheme.typography.bodySmall, color = ManTheme.colors.muted, modifier = Modifier.testTag("search_none"))
        }
        state.results.forEach { hit ->
            ManCard(modifier = Modifier.fillMaxWidth().testTag("hit_${hit.user.id}")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(hit.user.name, style = MaterialTheme.typography.titleMedium)
                        Text("@${hit.user.username} · Level ${hit.user.level}", style = MaterialTheme.typography.bodySmall, color = ManTheme.colors.muted)
                    }
                    when (hit.relation) {
                        Relation.NONE -> ManButton(text = "Add", onClick = { actions.onSend(hit) }, modifier = Modifier.testTag("hit_add_${hit.user.id}"))
                        Relation.REQUEST_SENT -> Text("Requested", style = MaterialTheme.typography.labelLarge, color = ManTheme.colors.muted, modifier = Modifier.testTag("hit_sent_${hit.user.id}"))
                        Relation.REQUEST_RECEIVED ->
                            Text("Asked you. Answer in Friends", style = MaterialTheme.typography.labelLarge, color = ManTheme.colors.muted, modifier = Modifier.testTag("hit_received_${hit.user.id}"))
                        Relation.FRIEND -> Text("Friends", style = MaterialTheme.typography.labelLarge, color = ManTheme.colors.success, modifier = Modifier.testTag("hit_friend_${hit.user.id}"))
                    }
                }
            }
        }

        SectionTitle("Your invite code")
        val code = state.code
        when {
            code != null ->
                ManCard(modifier = Modifier.fillMaxWidth().testTag("my_code")) {
                    Box(modifier = Modifier.size(220.dp).clip(ManShapes.Card).align(Alignment.CenterHorizontally)) {
                        QrCode(text = InviteLink.toLink(code.code), description = "QR code for your invite code ${code.code}")
                    }
                    Text(
                        code.code,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.align(Alignment.CenterHorizontally).testTag("my_code_text"),
                    )
                    Text(
                        "Anyone who scans this or types the code becomes your friend straight away. It works until ${code.expiresAt.take(10)}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ManTheme.colors.muted,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ManButton(text = "Share", onClick = { actions.onShare(code.code) }, modifier = Modifier.weight(1f).testTag("code_share"))
                        ManOutlinedButton(
                            text = if (state.regenerating) "Making..." else "New code",
                            onClick = actions.onRegenerate,
                            enabled = !state.regenerating,
                            modifier = Modifier.weight(1f).testTag("code_regenerate"),
                        )
                    }
                }
            state.codeError != null -> ErrorState(message = state.codeError, onRetry = actions.onRetryCode)
            else -> LoadingState(rows = 2)
        }

        SectionTitle("Have a code?")
        ManTextField(
            value = state.codeInput,
            onValueChange = actions.onCodeInput,
            label = "Friend's code",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth().testTag("code_input"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ManButton(
                text = "Add friend",
                onClick = actions.onRedeem,
                enabled = state.codeInput.isNotBlank() && !state.redeeming,
                loading = state.redeeming,
                modifier = Modifier.weight(1f).testTag("code_redeem"),
            )
            ManOutlinedButton(text = "Scan QR", onClick = actions.onScan, modifier = Modifier.weight(1f).testTag("code_scan"))
        }
        Text(
            "People only see your username, name and level. Never your email or body data.",
            style = MaterialTheme.typography.bodySmall,
            color = ManTheme.colors.muted,
        )
    }
}

// ====================================================================== privacy

class PrivacyActions(
    val onFeed: (Boolean) -> Unit,
    val onPrWeights: (Boolean) -> Unit,
    val onLeaderboard: (Boolean) -> Unit,
    val onUnblock: (Friend) -> Unit,
    val onRetry: () -> Unit,
)

@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    viewModel: PrivacyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SocialEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }
    WorkoutScaffold(title = "Privacy", onBack = onBack, snackbarHostState = snackbar) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            PrivacyContent(
                state,
                PrivacyActions(
                    onFeed = viewModel::setShowFeed,
                    onPrWeights = viewModel::setShowPrWeights,
                    onLeaderboard = viewModel::setShowOnLeaderboard,
                    onUnblock = viewModel::unblock,
                    onRetry = viewModel::load,
                ),
            )
        }
    }
}

@Composable
fun PrivacyContent(
    state: PrivacyUiState,
    actions: PrivacyActions,
) {
    if (state.loading) {
        LoadingState()
        return
    }
    if (state.error != null) {
        ErrorState(message = state.error, onRetry = actions.onRetry)
        return
    }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp).testTag("privacy"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("What your friends can see", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        SwitchRow("Show my activity in friends' feeds", "Workouts, Lock-in sessions, PRs, badges and level ups. Never body weight.", state.privacy.showFeed, "privacy_feed", actions.onFeed)
        SwitchRow("Show PR weights", "Off: friends see that you set a PR, not how heavy it was.", state.privacy.showPrWeights, "privacy_pr", actions.onPrWeights)
        SwitchRow("Appear on friends' leaderboards", "Rankings use effort (workouts, streaks, progress), never weights.", state.privacy.showOnLeaderboard, "privacy_board", actions.onLeaderboard)

        SectionTitle("Blocked people")
        if (state.blocked.isEmpty()) {
            Text("Nobody is blocked.", style = MaterialTheme.typography.bodyMedium, color = ManTheme.colors.muted, modifier = Modifier.testTag("blocked_none"))
        }
        state.blocked.forEach { friend ->
            ManCard(modifier = Modifier.fillMaxWidth().testTag("blocked_${friend.user.id}")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(friend.user.name, style = MaterialTheme.typography.titleMedium)
                        Text("@${friend.user.username}", style = MaterialTheme.typography.bodySmall, color = ManTheme.colors.muted)
                    }
                    ManTextButton(text = "Unblock", onClick = { actions.onUnblock(friend) }, modifier = Modifier.testTag("unblock_${friend.user.id}"))
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    tag: String,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
                .padding(vertical = 8.dp)
                .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = ManTheme.colors.muted)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
