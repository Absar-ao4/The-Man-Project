package com.example.fitapp.feature

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.feature.social.domain.Duel
import com.example.fitapp.feature.social.domain.DuelMetric
import com.example.fitapp.feature.social.domain.DuelStatus
import com.example.fitapp.feature.social.domain.FeedItem
import com.example.fitapp.feature.social.domain.FeedType
import com.example.fitapp.feature.social.domain.Friend
import com.example.fitapp.feature.social.domain.FriendRequest
import com.example.fitapp.feature.social.domain.InviteCode
import com.example.fitapp.feature.social.domain.Leaderboard
import com.example.fitapp.feature.social.domain.LeaderboardEntry
import com.example.fitapp.feature.social.domain.LeaderboardMetric
import com.example.fitapp.feature.social.domain.Privacy
import com.example.fitapp.feature.social.domain.Relation
import com.example.fitapp.feature.social.domain.SearchHit
import com.example.fitapp.feature.social.domain.UserCard
import com.example.fitapp.feature.social.ui.AddFriendActions
import com.example.fitapp.feature.social.ui.AddFriendContent
import com.example.fitapp.feature.social.ui.AddFriendUiState
import com.example.fitapp.feature.social.ui.DuelsActions
import com.example.fitapp.feature.social.ui.DuelsContent
import com.example.fitapp.feature.social.ui.DuelsUiState
import com.example.fitapp.feature.social.ui.FeedActions
import com.example.fitapp.feature.social.ui.FeedContent
import com.example.fitapp.feature.social.ui.FeedUiState
import com.example.fitapp.feature.social.ui.FriendsActions
import com.example.fitapp.feature.social.ui.FriendsContent
import com.example.fitapp.feature.social.ui.FriendsUiState
import com.example.fitapp.feature.social.ui.LeagueActions
import com.example.fitapp.feature.social.ui.LeagueContent
import com.example.fitapp.feature.social.ui.LeagueUiState
import com.example.fitapp.feature.social.ui.NewDuel
import com.example.fitapp.feature.social.ui.PrivacyActions
import com.example.fitapp.feature.social.ui.PrivacyContent
import com.example.fitapp.feature.social.ui.PrivacyUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class SocialScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 3, 11)

    private fun card(
        id: String,
        name: String = "Friend $id",
    ) = UserCard(id, "user_$id", name, 3, "Regular")

    private fun friend(id: String) = Friend(card(id), "2026-03-01T10:00:00Z")

    // ------------------------------------------------------------------ league

    private class LeagueCalls {
        val metrics = mutableListOf<LeaderboardMetric>()
        var prev = 0
        var next = 0
        var retry = 0
        var add = 0

        fun actions() = LeagueActions({ metrics += it }, { prev++ }, { next++ }, { retry++ }, { add++ })
    }

    private fun board(vararg entries: LeaderboardEntry) = Leaderboard(LeaderboardMetric.XP, "2026-W11", "XP", entries.toList())

    private fun showLeague(
        state: LeagueUiState,
        calls: LeagueCalls = LeagueCalls(),
    ) = compose.setContent { ManTheme { LeagueContent(state, today, calls.actions()) } }

    private fun league(
        board: Leaderboard? = null,
        loading: Boolean = false,
        error: String? = null,
        saved: Boolean = false,
        week: String = "2026-W11",
        metric: LeaderboardMetric = LeaderboardMetric.XP,
    ) = LeagueUiState(metric = metric, week = week, currentWeek = "2026-W11", loading = loading, leaderboard = board, error = error, savedCopy = saved)

    @Test
    fun theLeagueListsRanksNamesAndValuesAndMarksYou() {
        showLeague(league(board(LeaderboardEntry(1, card("b"), 300.0, false), LeaderboardEntry(2, card("a"), 120.0, true))))

        compose.onNodeWithTag("league_week").assertTextEquals("This week")
        compose.onNodeWithTag("league_row_0").assertTextContains("Friend b", substring = true)
        compose.onNodeWithTag("league_value_0").assertTextEquals("300 XP")
        compose.onNodeWithTag("league_row_1").assertTextContains("(you)", substring = true)
        compose.onNodeWithTag("league_value_1").assertTextEquals("120 XP")
    }

    @Test
    fun aLeagueOfOneInvitesYouToAddFriends() {
        val calls = LeagueCalls()
        showLeague(league(board(LeaderboardEntry(1, card("a"), 0.0, true))), calls)

        compose.onNodeWithText("A league needs friends").assertIsDisplayed()
        compose.onNodeWithText("Add a friend").performClick()
        assertEquals(1, calls.add)
    }

    @Test
    fun metricAndWeekControlsReportTheirClicks() {
        val calls = LeagueCalls()
        showLeague(league(board(LeaderboardEntry(1, card("a"), 1.0, true), LeaderboardEntry(2, card("b"), 1.0, false)), week = "2026-W10"), calls)

        compose.onNodeWithTag("league_metric_STREAK").performClick()
        compose.onNodeWithTag("league_prev").performClick()
        compose.onNodeWithTag("league_next").assertIsEnabled().performClick()
        compose.onNodeWithTag("league_week").assertTextEquals("Last week")
        compose.onNodeWithTag("league_metric_XP").assertIsSelected()

        assertEquals(listOf(LeaderboardMetric.STREAK), calls.metrics)
        assertEquals(1, calls.prev)
        assertEquals(1, calls.next)
    }

    @Test
    fun youCanNotLookAheadOfThisWeek() {
        showLeague(league(board(LeaderboardEntry(1, card("a"), 1.0, true), LeaderboardEntry(2, card("b"), 1.0, false))))

        compose.onNodeWithTag("league_next").assertIsNotEnabled()
    }

    @Test
    fun someoneWithNoDataIsListedWithoutARank() {
        showLeague(league(board(LeaderboardEntry(null, card("a"), null, true), LeaderboardEntry(null, card("b"), null, false)), metric = LeaderboardMetric.IMPROVEMENT))

        compose.onNodeWithTag("league_value_0").assertTextEquals("No data yet")
    }

    @Test
    fun anOfflineLeagueSaysItIsAnOldCopy() {
        val calls = LeagueCalls()
        showLeague(league(board(LeaderboardEntry(1, card("a"), 5.0, true), LeaderboardEntry(2, card("b"), 4.0, false)), saved = true), calls)

        compose.onNodeWithTag("saved_copy").assertIsDisplayed()
        compose.onNodeWithText("Refresh").performClick()
        assertEquals(1, calls.retry)
    }

    @Test
    fun aLeagueThatCouldNotLoadShowsAnErrorWithRetry() {
        val calls = LeagueCalls()
        showLeague(league(error = "You seem to be offline."), calls)

        compose.onNodeWithText("You seem to be offline.").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(1, calls.retry)
    }

    // ------------------------------------------------------------------ feed

    private class FeedCalls {
        var retry = 0
        var more = 0
        var add = 0

        fun actions() = FeedActions({ retry++ }, { more++ }, { add++ })
    }

    private fun feedItem(
        id: String,
        type: FeedType = FeedType.WORKOUT_FINISHED,
        payload: Map<String, String> = mapOf("name" to "Push day", "minutes" to "55"),
    ) = FeedItem(id, card("f"), type, payload, "2026-03-02T10:00:00Z")

    private fun showFeed(
        state: FeedUiState,
        calls: FeedCalls = FeedCalls(),
    ) = compose.setContent { ManTheme { FeedContent(state, calls.actions()) } }

    @Test
    fun theFeedReadsAsSentencesWithoutPrWeightsUnlessShared() {
        showFeed(
            FeedUiState(
                items = listOf(feedItem("1"), feedItem("2", FeedType.PR, mapOf("exercise" to "Bench Press")), feedItem("3", FeedType.PR, mapOf("exercise" to "Squat", "weightKg" to "140.0", "reps" to "3"))),
                loading = false,
            ),
        )

        compose.onNodeWithTag("feed_item_1").assertTextContains("Friend f finished Push day (55 min)", substring = true)
        compose.onNodeWithTag("feed_item_2").assertTextContains("set a PR on Bench Press", substring = true)
        compose.onNodeWithTag("feed_item_3").assertTextContains("140 kg x 3", substring = true)
    }

    @Test
    fun anEmptyFeedInvitesYouToAddFriends() {
        val calls = FeedCalls()
        showFeed(FeedUiState(loading = false), calls)

        compose.onNodeWithText("Nothing yet").assertIsDisplayed()
        compose.onNodeWithText("Add a friend").performClick()
        assertEquals(1, calls.add)
    }

    @Test
    fun aFeedThatCouldNotLoadShowsAnError() {
        val calls = FeedCalls()
        showFeed(FeedUiState(loading = false, error = "You seem to be offline."), calls)

        compose.onNodeWithText("Retry").performClick()
        assertEquals(1, calls.retry)
    }

    // ------------------------------------------------------------------ friends

    private class FriendCalls {
        val accepted = mutableListOf<String>()
        val declined = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        val removed = mutableListOf<String>()
        val blocked = mutableListOf<String>()
        var add = 0

        fun actions() =
            FriendsActions(
                onRetry = {},
                onAddFriend = { add++ },
                onAccept = { accepted += it.id },
                onDecline = { declined += it.id },
                onCancel = { cancelled += it.id },
                onRemove = { removed += it.user.id },
                onBlock = { blocked += it.user.id },
            )
    }

    private fun request(
        id: String,
        from: String,
        to: String,
    ) = FriendRequest(id, card(from), card(to), "2026-03-02T10:00:00Z")

    private fun showFriends(
        state: FriendsUiState,
        calls: FriendCalls = FriendCalls(),
    ) = compose.setContent { ManTheme { FriendsContent(state, calls.actions()) } }

    @Test
    fun requestsCanBeAcceptedDeclinedAndCancelled() {
        val calls = FriendCalls()
        showFriends(
            FriendsUiState(
                loading = false,
                friends = listOf(friend("a")),
                incoming = listOf(request("in", "b", "me")),
                outgoing = listOf(request("out", "me", "c")),
            ),
            calls,
        )

        compose.onNodeWithTag("accept_in").performScrollTo().performClick()
        compose.onNodeWithTag("decline_in").performScrollTo().performClick()
        compose.onNodeWithTag("cancel_out").performScrollTo().performClick()

        assertEquals(listOf("in"), calls.accepted)
        assertEquals(listOf("in"), calls.declined)
        assertEquals(listOf("out"), calls.cancelled)
    }

    @Test
    fun buttonsAreOffWhileSomethingIsRunning() {
        showFriends(FriendsUiState(loading = false, incoming = listOf(request("in", "b", "me")), busy = true))

        compose.onNodeWithTag("accept_in").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("decline_in").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun aFriendCanBeRemovedOrBlockedAfterAConfirmingDialog() {
        val calls = FriendCalls()
        showFriends(FriendsUiState(loading = false, friends = listOf(friend("a"), friend("z"))), calls)

        compose.onNodeWithTag("friend_a").performScrollTo().performClick()
        compose.onNodeWithTag("friend_remove").performClick()
        compose.onNodeWithTag("friend_z").performScrollTo().performClick()
        compose.onNodeWithTag("friend_block").performClick()

        assertEquals(listOf("a"), calls.removed)
        assertEquals(listOf("z"), calls.blocked)
    }

    @Test
    fun noFriendsAtAllInvitesYouToAddSome() {
        val calls = FriendCalls()
        showFriends(FriendsUiState(loading = false), calls)

        compose.onNodeWithText("No friends yet").assertIsDisplayed()
        compose.onNodeWithText("Add a friend").performClick()
        assertEquals(1, calls.add)
    }

    // ------------------------------------------------------------------ duels

    private class DuelCalls {
        val accepted = mutableListOf<String>()
        val declined = mutableListOf<String>()
        val withdrawn = mutableListOf<String>()
        val opponents = mutableListOf<String>()
        val metrics = mutableListOf<DuelMetric>()
        var new = 0
        var send = 0
        var cancelNew = 0

        fun actions() =
            DuelsActions(
                onRetry = {},
                onNew = { new++ },
                onAccept = { accepted += it.id },
                onDecline = { declined += it.id },
                onWithdraw = { withdrawn += it.id },
                onChooseOpponent = { opponents += it },
                onChooseMetric = { metrics += it },
                onSend = { send++ },
                onCancelNew = { cancelNew++ },
            )
    }

    private fun duel(
        id: String,
        status: DuelStatus,
        challenger: Boolean,
        mine: Int = 0,
        theirs: Int = 0,
        winner: String? = null,
    ): Duel {
        val me = card("me", "Me")
        val other = card("o$id", "Rival $id")
        return Duel(
            id = id,
            metric = DuelMetric.WORKOUTS,
            status = status,
            challenger = if (challenger) me else other,
            opponent = if (challenger) other else me,
            startsAt = "2026-03-02T10:00:00Z",
            endsAt = "2026-03-09T10:00:00Z",
            challengerScore = if (challenger) mine else theirs,
            opponentScore = if (challenger) theirs else mine,
            winnerId = winner,
            youAreChallenger = challenger,
        )
    }

    private fun showDuels(
        state: DuelsUiState,
        calls: DuelCalls = DuelCalls(),
    ) = compose.setContent { ManTheme { DuelsContent(state, calls.actions()) } }

    @Test
    fun duelsShowTheirStateScoresAndTheRightButtons() {
        val calls = DuelCalls()
        showDuels(
            DuelsUiState(
                loading = false,
                duels =
                    listOf(
                        duel("ask", DuelStatus.PENDING, challenger = false),
                        duel("run", DuelStatus.ACTIVE, challenger = true, mine = 3, theirs = 1),
                        duel("sent", DuelStatus.PENDING, challenger = true),
                        duel("won", DuelStatus.FINISHED, challenger = true, mine = 4, theirs = 2, winner = "me"),
                    ),
            ),
            calls,
        )

        compose.onNodeWithTag("duel_status_ask").performScrollTo().assertTextEquals("Rival ask challenged you")
        compose.onNodeWithTag("duel_status_run").performScrollTo().assertTextEquals("You are ahead")
        compose.onNodeWithTag("duel_bar_run").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("duel_status_won").performScrollTo().assertTextEquals("You won")

        compose.onNodeWithTag("duel_accept_ask").performScrollTo().performClick()
        compose.onNodeWithTag("duel_decline_ask").performScrollTo().performClick()
        compose.onNodeWithTag("duel_withdraw_sent").performScrollTo().performClick()
        compose.onAllNodesWithTag("duel_accept_run").assertCountEquals(0)

        assertEquals(listOf("ask"), calls.accepted)
        assertEquals(listOf("ask"), calls.declined)
        assertEquals(listOf("sent"), calls.withdrawn)
    }

    @Test
    fun noDuelsExplainsHowTheyWorkAndOffersAChallenge() {
        val calls = DuelCalls()
        showDuels(DuelsUiState(loading = false), calls)

        compose.onNodeWithText("No duels").assertIsDisplayed()
        compose.onNodeWithTag("duel_new").performClick()
        assertEquals(1, calls.new)
    }

    @Test
    fun theChallengeDialogNeedsAFriendBeforeItCanBeSent() {
        val calls = DuelCalls()
        showDuels(DuelsUiState(loading = false, friends = listOf(friend("a")), creating = NewDuel()), calls)

        compose.onNodeWithTag("duel_send").assertIsNotEnabled()
        compose.onNodeWithTag("duel_opponent_a").performClick()
        compose.onNodeWithTag("duel_metric_XP").performClick()

        assertEquals(listOf("a"), calls.opponents)
        assertEquals(listOf(DuelMetric.XP), calls.metrics)
    }

    @Test
    fun aReadyChallengeCanBeSent() {
        val calls = DuelCalls()
        showDuels(DuelsUiState(loading = false, friends = listOf(friend("a")), creating = NewDuel(opponentId = "a", metric = DuelMetric.LOCKINS)), calls)

        compose.onNodeWithTag("duel_metric_LOCKINS").assertIsSelected()
        compose.onNodeWithTag("duel_opponent_a").assertIsSelected()
        compose.onNodeWithTag("duel_send").assertIsEnabled().performClick()
        compose.onNodeWithText("Cancel").performClick()

        assertEquals(1, calls.send)
        assertEquals(1, calls.cancelNew)
    }

    // ------------------------------------------------------------------ add a friend

    private class AddCalls {
        val queries = mutableListOf<String>()
        val sent = mutableListOf<String>()
        val codeInputs = mutableListOf<String>()
        var redeem = 0
        var scan = 0
        val shared = mutableListOf<String>()
        var regenerate = 0

        fun actions() =
            AddFriendActions(
                onQuery = { queries += it },
                onSend = { sent += it.user.id },
                onCodeInput = { codeInputs += it },
                onRedeem = { redeem++ },
                onScan = { scan++ },
                onShare = { shared += it },
                onRegenerate = { regenerate++ },
                onRetryCode = {},
            )
    }

    private fun showAdd(
        state: AddFriendUiState,
        calls: AddCalls = AddCalls(),
    ) = compose.setContent { ManTheme { AddFriendContent(state, calls.actions()) } }

    @Test
    fun searchResultsOfferTheRightAction() {
        val calls = AddCalls()
        showAdd(
            AddFriendUiState(
                query = "per",
                results =
                    listOf(
                        SearchHit(card("1"), Relation.NONE),
                        SearchHit(card("2"), Relation.REQUEST_SENT),
                        SearchHit(card("3"), Relation.REQUEST_RECEIVED),
                        SearchHit(card("4"), Relation.FRIEND),
                    ),
            ),
            calls,
        )

        compose.onNodeWithTag("hit_add_1").performScrollTo().performClick()
        compose.onNodeWithTag("hit_sent_2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("hit_received_3").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("hit_friend_4").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("friend_search").performTextInput("x")

        assertEquals(listOf("1"), calls.sent)
        assertEquals(listOf("perx"), calls.queries)
    }

    @Test
    fun noMatchesAndAShortQueryEachSaySo() {
        showAdd(AddFriendUiState(query = "nobody"))
        compose.onNodeWithTag("search_none").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun myCodeShowsAQrCodeTheCodeAndShareAndNewCodeButtons() {
        val calls = AddCalls()
        showAdd(AddFriendUiState(code = InviteCode("ABCD2345", "2026-03-16T10:00:00Z")), calls)

        compose.onNodeWithTag("my_code_text").performScrollTo().assertTextEquals("ABCD2345")
        compose.onNodeWithContentDescription("QR code for your invite code ABCD2345").assertIsDisplayed()
        compose.onNodeWithTag("code_share").performScrollTo().performClick()
        compose.onNodeWithTag("code_regenerate").performScrollTo().performClick()

        assertEquals(listOf("ABCD2345"), calls.shared)
        assertEquals(1, calls.regenerate)
    }

    @Test
    fun aFriendsCodeCanBeTypedOrScanned() {
        val calls = AddCalls()
        showAdd(AddFriendUiState(codeInput = "ZZZZ2222"), calls)

        compose.onNodeWithTag("code_input").performScrollTo().performTextInput("1")
        compose.onNodeWithTag("code_redeem").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("code_scan").performScrollTo().performClick()

        assertEquals(listOf("ZZZZ22221"), calls.codeInputs)
        assertEquals(1, calls.redeem)
        assertEquals(1, calls.scan)
    }

    @Test
    fun addFriendNeedsSomethingTyped() {
        showAdd(AddFriendUiState(codeInput = ""))

        compose.onNodeWithTag("code_redeem").performScrollTo().assertIsNotEnabled()
    }

    // ------------------------------------------------------------------ privacy

    private class PrivacyCalls {
        val feed = mutableListOf<Boolean>()
        val pr = mutableListOf<Boolean>()
        val board = mutableListOf<Boolean>()
        val unblocked = mutableListOf<String>()

        fun actions() =
            PrivacyActions(
                onFeed = { feed += it },
                onPrWeights = { pr += it },
                onLeaderboard = { board += it },
                onUnblock = { unblocked += it.user.id },
                onRetry = {},
            )
    }

    private fun showPrivacy(
        state: PrivacyUiState,
        calls: PrivacyCalls = PrivacyCalls(),
    ) = compose.setContent { ManTheme { PrivacyContent(state, calls.actions()) } }

    @Test
    fun theSwitchesReportTheOppositeOfTheirState() {
        val calls = PrivacyCalls()
        showPrivacy(PrivacyUiState(loading = false, privacy = Privacy(showFeed = true, showPrWeights = false, showOnLeaderboard = true)), calls)

        compose.onNodeWithTag("privacy_feed").performClick()
        compose.onNodeWithTag("privacy_pr").performClick()
        compose.onNodeWithTag("privacy_board").performClick()

        assertEquals(listOf(false), calls.feed)
        assertEquals(listOf(true), calls.pr)
        assertEquals(listOf(false), calls.board)
    }

    @Test
    fun blockedPeopleAreListedAndCanBeUnblocked() {
        val calls = PrivacyCalls()
        showPrivacy(PrivacyUiState(loading = false, blocked = listOf(friend("x"))), calls)

        compose.onNodeWithTag("unblock_x").performScrollTo().performClick()

        assertEquals(listOf("x"), calls.unblocked)
    }

    @Test
    fun withNobodyBlockedItSaysSo() {
        showPrivacy(PrivacyUiState(loading = false))

        compose.onNodeWithTag("blocked_none").performScrollTo().assertIsDisplayed()
    }
}
