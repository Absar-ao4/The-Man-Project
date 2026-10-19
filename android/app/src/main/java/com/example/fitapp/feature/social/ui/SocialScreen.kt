package com.example.fitapp.feature.social.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.fitapp.core.designsystem.components.ManCard
import com.example.fitapp.core.designsystem.components.ManChip
import com.example.fitapp.core.designsystem.components.ManTextButton
import com.example.fitapp.feature.social.domain.FeedText
import com.example.fitapp.feature.social.domain.IsoWeeks
import com.example.fitapp.feature.social.domain.LeaderboardMetric
import com.example.fitapp.feature.social.domain.LeaderboardText
import java.time.LocalDate

private val TAB_TITLES = listOf("League", "Friends", "Duels", "Feed")

/** The Social tab: the league, friends, duels and the feed of friends' activity. */
@Composable
fun SocialScreen(
    onAddFriend: () -> Unit,
    onPrivacy: () -> Unit,
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    Column(modifier = Modifier.fillMaxSize().testTag("social_screen")) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Social", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
            IconButton(onClick = onAddFriend, modifier = Modifier.testTag("social_add_friend")) {
                Icon(Icons.Filled.PersonAdd, contentDescription = "Add a friend")
            }
            IconButton(onClick = onPrivacy, modifier = Modifier.testTag("social_privacy")) {
                Icon(Icons.Filled.Lock, contentDescription = "Privacy settings")
            }
        }
        TabRow(selectedTabIndex = selected, containerColor = MaterialTheme.colorScheme.background) {
            TAB_TITLES.forEachIndexed { index, title ->
                Tab(
                    selected = selected == index,
                    onClick = { selected = index },
                    text = { Text(title) },
                    modifier = Modifier.testTag("social_tab_$index"),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            when (selected) {
                0 -> LeagueTab(onAddFriend)
                1 -> FriendsTab(onAddFriend, snackbar)
                2 -> DuelsTab(snackbar)
                else -> FeedTab(onAddFriend)
            }
        }
        SnackbarHost(snackbar)
    }
}

// ====================================================================== league

class LeagueActions(
    val onMetric: (LeaderboardMetric) -> Unit,
    val onPreviousWeek: () -> Unit,
    val onNextWeek: () -> Unit,
    val onRetry: () -> Unit,
    val onAddFriends: () -> Unit,
)

@Composable
private fun LeagueTab(onAddFriend: () -> Unit) {
    val viewModel: LeagueViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LeagueContent(
        state = state,
        today = viewModel.todayDate,
        actions =
            LeagueActions(
                onMetric = viewModel::setMetric,
                onPreviousWeek = viewModel::previousWeek,
                onNextWeek = viewModel::nextWeek,
                onRetry = viewModel::refresh,
                onAddFriends = onAddFriend,
            ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LeagueContent(
    state: LeagueUiState,
    today: LocalDate,
    actions: LeagueActions,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("league"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LeaderboardMetric.entries.forEach { metric ->
                ManChip(
                    label = metric.label,
                    selected = state.metric == metric,
                    onClick = { actions.onMetric(metric) },
                    modifier = Modifier.testTag("league_metric_${metric.name}"),
                )
            }
        }
        Text(state.metric.hint, style = MaterialTheme.typography.bodySmall, color = ManTheme.colors.muted)

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = actions.onPreviousWeek, modifier = Modifier.testTag("league_prev")) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous week")
            }
            Text(
                IsoWeeks.title(state.week, today),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).testTag("league_week"),
            )
            IconButton(onClick = actions.onNextWeek, enabled = state.canGoNext, modifier = Modifier.testTag("league_next")) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next week")
            }
        }

        val board = state.leaderboard
        when {
            state.loading && board == null -> LoadingState()
            board == null && state.error != null -> ErrorState(message = state.error, onRetry = actions.onRetry)
            board != null -> {
                if (state.savedCopy) SavedCopyNote(actions.onRetry)
                if (board.entries.size <= 1) {
                    EmptyState(
                        title = "A league needs friends",
                        message = "Add a friend to compare weekly effort. Rankings use workouts, streaks and progress, never body weight or how heavy you lift.",
                        actionLabel = "Add a friend",
                        onAction = actions.onAddFriends,
                    )
                }
                board.entries.forEachIndexed { index, entry ->
                    ManCard(modifier = Modifier.fillMaxWidth().testTag("league_row_$index")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                entry.rank?.toString() ?: "-",
                                style = MaterialTheme.typography.titleLarge,
                                color = if (entry.rank == 1) ManTheme.colors.success else ManTheme.colors.muted,
                                modifier = Modifier.padding(end = 16.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    entry.user.name + if (entry.you) " (you)" else "",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text("Level ${entry.user.level} · ${entry.user.title}", style = MaterialTheme.typography.bodySmall, color = ManTheme.colors.muted)
                            }
                            Text(
                                LeaderboardText.value(state.metric, entry.value),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.testTag("league_value_$index"),
                            )
                        }
                    }
                }
                Text(
                    "Weeks run Monday to Sunday in each person's own time zone. Sets that look implausible earn no XP until they are checked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ManTheme.colors.muted,
                )
            }
        }
    }
}

@Composable
private fun SavedCopyNote(onRefresh: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("saved_copy")) {
        Text(
            "Showing what was saved earlier. You seem to be offline.",
            style = MaterialTheme.typography.bodySmall,
            color = ManTheme.colors.muted,
            modifier = Modifier.weight(1f),
        )
        ManTextButton(text = "Refresh", onClick = onRefresh)
    }
}

// ====================================================================== feed

class FeedActions(
    val onRetry: () -> Unit,
    val onLoadMore: () -> Unit,
    val onAddFriends: () -> Unit,
)

@Composable
private fun FeedTab(onAddFriend: () -> Unit) {
    val viewModel: FeedViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    FeedContent(state, FeedActions(onRetry = viewModel::refresh, onLoadMore = viewModel::loadMore, onAddFriends = onAddFriend))
}

@Composable
fun FeedContent(
    state: FeedUiState,
    actions: FeedActions,
) {
    when {
        state.loading && state.items.isEmpty() -> LoadingState()
        state.error != null && state.items.isEmpty() -> ErrorState(message = state.error, onRetry = actions.onRetry)
        state.isEmpty ->
            EmptyState(
                title = "Nothing yet",
                message = "Workouts, PRs, badges and level ups from friends who share their feed show up here.",
                actionLabel = "Add a friend",
                onAction = actions.onAddFriends,
            )
        else -> {
            val listState = rememberLazyListState()
            val nearEnd by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= listState.layoutInfo.totalItemsCount - 3 } == true } }
            LaunchedEffect(nearEnd, state.nextCursor) {
                if (nearEnd && state.nextCursor != null) actions.onLoadMore()
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("feed_list"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.savedCopy) {
                    item { SavedCopyNote(actions.onRetry) }
                }
                items(state.items, key = { it.id }) { item ->
                    ManCard(modifier = Modifier.fillMaxWidth().testTag("feed_item_${item.id}")) {
                        Text(FeedText.describe(item), style = MaterialTheme.typography.bodyMedium)
                        Text(item.createdAt.take(10), style = MaterialTheme.typography.bodySmall, color = ManTheme.colors.muted)
                    }
                }
                if (state.loadingMore) {
                    item { LoadingState(rows = 1) }
                }
            }
        }
    }
}
