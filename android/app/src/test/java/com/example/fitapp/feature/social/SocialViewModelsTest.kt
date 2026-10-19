package com.example.fitapp.feature.social

import app.cash.turbine.test
import com.example.fitapp.MainDispatcherRule
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.fakes.FakeDeviceInfo
import com.example.fitapp.fakes.FakeSocialRepository
import com.example.fitapp.fakes.card
import com.example.fitapp.fakes.duel
import com.example.fitapp.fakes.feedItem
import com.example.fitapp.fakes.friend
import com.example.fitapp.fakes.friendRequest
import com.example.fitapp.fakes.hit
import com.example.fitapp.fakes.loaded
import com.example.fitapp.fakes.offlineWith
import com.example.fitapp.feature.social.domain.DuelMetric
import com.example.fitapp.feature.social.domain.DuelStatus
import com.example.fitapp.feature.social.domain.FeedPage
import com.example.fitapp.feature.social.domain.InviteCode
import com.example.fitapp.feature.social.domain.Leaderboard
import com.example.fitapp.feature.social.domain.LeaderboardEntry
import com.example.fitapp.feature.social.domain.LeaderboardMetric
import com.example.fitapp.feature.social.domain.Privacy
import com.example.fitapp.feature.social.domain.Relation
import com.example.fitapp.feature.social.domain.Requests
import com.example.fitapp.feature.social.domain.SocialLoad
import com.example.fitapp.feature.social.ui.AddFriendUiState
import com.example.fitapp.feature.social.ui.AddFriendViewModel
import com.example.fitapp.feature.social.ui.DuelsViewModel
import com.example.fitapp.feature.social.ui.FeedViewModel
import com.example.fitapp.feature.social.ui.FriendsViewModel
import com.example.fitapp.feature.social.ui.LeagueViewModel
import com.example.fitapp.feature.social.ui.PrivacyViewModel
import com.example.fitapp.feature.social.ui.SocialEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class LeagueViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repo = FakeSocialRepository()
    private val today = LocalDate.of(2026, 3, 11) // 2026-W11

    private fun vm() = LeagueViewModel(repo, FakeDeviceInfo(today = today))

    @Test
    fun `it opens on this week and asks for no particular week`() =
        runTest {
            val vm = vm()

            val s = vm.state.value
            assertEquals("2026-W11", s.week)
            assertEquals(LeaderboardMetric.XP, s.metric)
            assertFalse(s.loading)
            assertEquals(listOf(LeaderboardMetric.XP to null), repo.leaderboardAsks)
            assertFalse(s.canGoNext)
            assertEquals(1, s.leaderboard!!.entries.size)
        }

    @Test
    fun `choosing another metric loads it for the same week`() =
        runTest {
            val vm = vm()

            vm.setMetric(LeaderboardMetric.STREAK)
            vm.setMetric(LeaderboardMetric.STREAK) // the same again does nothing

            assertEquals(listOf(LeaderboardMetric.XP to null, LeaderboardMetric.STREAK to null), repo.leaderboardAsks)
            assertEquals(LeaderboardMetric.STREAK, vm.state.value.metric)
        }

    @Test
    fun `weeks can be stepped back and forward but not past this week`() =
        runTest {
            val vm = vm()

            vm.previousWeek()
            assertEquals("2026-W10", vm.state.value.week)
            assertEquals("2026-W10", repo.leaderboardAsks.last().second)
            assertTrue(vm.state.value.canGoNext)
            vm.previousWeek()
            assertEquals("2026-W09", vm.state.value.week)

            vm.nextWeek()
            vm.nextWeek()
            assertEquals("2026-W11", vm.state.value.week)
            assertNull(repo.leaderboardAsks.last().second) // back on this week: no week is sent
            val asks = repo.leaderboardAsks.size
            vm.nextWeek()
            assertEquals(asks, repo.leaderboardAsks.size)
        }

    @Test
    fun `a saved copy is shown with a note when the network fails`() =
        runTest {
            val board = Leaderboard(LeaderboardMetric.XP, "2026-W11", "XP", listOf(LeaderboardEntry(1, card("a"), 50.0, true)))
            repo.leaderboards = { _, _ -> offlineWith(board) }

            val vm = vm()

            assertTrue(vm.state.value.savedCopy)
            assertEquals(50.0, vm.state.value.leaderboard!!.entries[0].value!!, 0.0)
        }

    @Test
    fun `with nothing saved and no network the error shows and can be retried`() =
        runTest {
            repo.leaderboards = { _, _ -> offlineWith(null) }
            val vm = vm()
            assertEquals(ApiFailure.Offline.message, vm.state.value.error)
            assertNull(vm.state.value.leaderboard)

            repo.leaderboards = { m, w -> loaded(Leaderboard(m, w ?: "2026-W11", "", emptyList())) }
            vm.refresh()

            assertNull(vm.state.value.error)
            assertFalse(vm.state.value.savedCopy)
        }
}

class FeedViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repo = FakeSocialRepository()

    @Test
    fun `the first page loads and older pages are appended without repeats`() =
        runTest {
            repo.feedPages =
                mapOf(
                    null to loaded(FeedPage(listOf(feedItem("1"), feedItem("2")), "c1")),
                    "c1" to loaded(FeedPage(listOf(feedItem("2"), feedItem("3")), null)),
                )
            val vm = FeedViewModel(repo)
            assertEquals(listOf("1", "2"), vm.state.value.items.map { it.id })

            vm.loadMore()

            assertEquals(listOf("1", "2", "3"), vm.state.value.items.map { it.id })
            assertNull(vm.state.value.nextCursor)
            assertEquals(listOf(null, "c1"), repo.feedAsks)
            vm.loadMore()
            assertEquals(2, repo.feedAsks.size) // nothing more to load
        }

    @Test
    fun `a failed load of an older page keeps what is on screen`() =
        runTest {
            repo.feedPages =
                mapOf(
                    null to loaded(FeedPage(listOf(feedItem("1")), "c1")),
                    "c1" to offlineWith(null),
                )
            val vm = FeedViewModel(repo)

            vm.loadMore()

            assertEquals(listOf("1"), vm.state.value.items.map { it.id })
            assertEquals(ApiFailure.Offline.message, vm.state.value.error)
            assertFalse(vm.state.value.loadingMore)
        }

    @Test
    fun `the saved copy has no older pages`() =
        runTest {
            repo.feedPages = mapOf(null to offlineWith(FeedPage(listOf(feedItem("1")), "c1")))

            val vm = FeedViewModel(repo)

            assertTrue(vm.state.value.savedCopy)
            assertNull(vm.state.value.nextCursor)
            vm.loadMore()
            assertEquals(listOf<String?>(null), repo.feedAsks)
        }

    @Test
    fun `an empty feed is told apart from an error`() =
        runTest {
            val empty = FeedViewModel(repo)
            assertTrue(empty.state.value.isEmpty)

            repo.feedPages = mapOf(null to offlineWith<FeedPage>(null))
            val failed = FeedViewModel(repo)
            assertFalse(failed.state.value.isEmpty)
            assertEquals(ApiFailure.Offline.message, failed.state.value.error)
        }
}

class FriendsViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repo = FakeSocialRepository()

    @Test
    fun `friends and both request lists load together`() =
        runTest {
            repo.friends = loaded(listOf(friend("a")))
            repo.requests = loaded(Requests(listOf(friendRequest("r1", "b", "me")), listOf(friendRequest("r2", "me", "c"))))

            val vm = FriendsViewModel(repo)

            val s = vm.state.value
            assertEquals(1, s.friends.size)
            assertEquals(listOf("r1"), s.incoming.map { it.id })
            assertEquals(listOf("r2"), s.outgoing.map { it.id })
            assertFalse(s.isEmpty)
        }

    @Test
    fun `nothing at all is an empty state and an error is not`() =
        runTest {
            assertTrue(FriendsViewModel(repo).state.value.isEmpty)

            repo.friends = offlineWith<List<com.example.fitapp.feature.social.domain.Friend>>(null)
            val failed = FriendsViewModel(repo)
            assertFalse(failed.state.value.isEmpty)
            assertEquals(ApiFailure.Offline.message, failed.state.value.error)
        }

    @Test
    fun `accepting a request says so and refreshes`() =
        runTest {
            repo.requests = loaded(Requests(listOf(friendRequest("r1", "b", "me")), emptyList()))
            val vm = FriendsViewModel(repo)

            vm.events.test {
                repo.friends = loaded(listOf(friend("b", "Bea")))
                repo.requests = loaded(Requests(emptyList(), emptyList()))
                vm.accept(vm.state.value.incoming.single())

                assertEquals(SocialEvent.Message("You are now friends with Friend b."), awaitItem())
            }
            assertEquals(listOf("accept:r1"), repo.calls)
            assertEquals(1, vm.state.value.friends.size)
            assertTrue(vm.state.value.incoming.isEmpty())
            assertFalse(vm.state.value.busy)
        }

    @Test
    fun `a failed action shows the reason and leaves the lists alone`() =
        runTest {
            repo.friends = loaded(listOf(friend("a")))
            val vm = FriendsViewModel(repo)
            repo.actionResult = AppResult.Failure(ApiFailure.Offline)

            vm.events.test {
                vm.remove(vm.state.value.friends.single())

                assertEquals(SocialEvent.Message(ApiFailure.Offline.message), awaitItem())
            }
            assertEquals(1, vm.state.value.friends.size)
            assertFalse(vm.state.value.busy)
        }

    @Test
    fun `decline, cancel and block go to the right calls`() =
        runTest {
            repo.friends = loaded(listOf(friend("a")))
            repo.requests = loaded(Requests(listOf(friendRequest("in", "b", "me")), listOf(friendRequest("out", "me", "c"))))
            val vm = FriendsViewModel(repo)

            vm.decline(vm.state.value.incoming.single())
            vm.cancel(vm.state.value.outgoing.single())
            vm.block(vm.state.value.friends.single())

            assertEquals(listOf("decline:in", "cancel:out", "block:a"), repo.calls)
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AddFriendViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repo = FakeSocialRepository()

    @Test
    fun `my invite code loads on open`() =
        runTest {
            val vm = AddFriendViewModel(repo)

            assertEquals("ABCD2345", vm.state.value.code!!.code)
            assertNull(vm.state.value.codeError)
        }

    @Test
    fun `a code that cannot be loaded shows why and can be retried`() =
        runTest {
            repo.code = offlineWith<InviteCode>(null)
            val vm = AddFriendViewModel(repo)
            assertEquals(ApiFailure.Offline.message, vm.state.value.codeError)

            repo.code = loaded(InviteCode("ZZZZ2222", "2026-03-30T10:00:00Z"))
            vm.retryCode()

            assertEquals("ZZZZ2222", vm.state.value.code!!.code)
            assertNull(vm.state.value.codeError)
        }

    @Test
    fun `typing waits for a pause before searching and a very short query never searches`() =
        runTest {
            repo.searchResult = AppResult.Success(listOf(hit("1")))
            val vm = AddFriendViewModel(repo)

            vm.setQuery("a")
            advanceTimeBy(AddFriendViewModel.SEARCH_PAUSE_MS + 1)
            assertTrue(repo.searches.isEmpty())

            vm.setQuery("al")
            vm.setQuery("ali")
            advanceTimeBy(AddFriendViewModel.SEARCH_PAUSE_MS - 100)
            assertTrue(repo.searches.isEmpty())
            advanceTimeBy(200)

            assertEquals(listOf("ali"), repo.searches)
            assertEquals(1, vm.state.value.results.size)
            assertFalse(vm.state.value.searching)
        }

    @Test
    fun `clearing the query clears the results and a failed search says why`() =
        runTest {
            repo.searchResult = AppResult.Success(listOf(hit("1")))
            val vm = AddFriendViewModel(repo)
            vm.setQuery("alice")
            advanceTimeBy(AddFriendViewModel.SEARCH_PAUSE_MS + 1)
            assertEquals(1, vm.state.value.results.size)

            vm.setQuery("")
            assertTrue(vm.state.value.results.isEmpty())

            repo.searchResult = AppResult.Failure(ApiFailure.Offline)
            vm.setQuery("bob")
            advanceTimeBy(AddFriendViewModel.SEARCH_PAUSE_MS + 1)
            assertEquals(ApiFailure.Offline.message, vm.state.value.searchError)
            assertTrue(vm.state.value.results.isEmpty())
        }

    @Test
    fun `an at sign does not count towards the minimum length`() {
        assertFalse(AddFriendUiState(query = "@a").canSearch)
        assertTrue(AddFriendUiState(query = "@ab").canSearch)
    }

    @Test
    fun `sending a request marks that person as requested`() =
        runTest {
            repo.searchResult = AppResult.Success(listOf(hit("1"), hit("2", Relation.FRIEND)))
            val vm = AddFriendViewModel(repo)
            vm.setQuery("per")
            advanceTimeBy(AddFriendViewModel.SEARCH_PAUSE_MS + 1)

            vm.events.test {
                vm.sendRequest(vm.state.value.results[0])

                assertEquals(SocialEvent.Message("Request sent to Person 1."), awaitItem())
            }
            assertEquals(listOf<Pair<String?, String?>>(null to "1"), repo.sentRequests)
            assertEquals(Relation.REQUEST_SENT, vm.state.value.results[0].relation)
            assertEquals(Relation.FRIEND, vm.state.value.results[1].relation)
        }

    @Test
    fun `a refused request tells the user and changes nothing`() =
        runTest {
            repo.searchResult = AppResult.Success(listOf(hit("1")))
            val vm = AddFriendViewModel(repo)
            vm.setQuery("per")
            advanceTimeBy(AddFriendViewModel.SEARCH_PAUSE_MS + 1)
            repo.actionResult = AppResult.Failure(ApiFailure.Conflict("You already sent this person a request"))

            vm.events.test {
                vm.sendRequest(vm.state.value.results[0])
                assertEquals(SocialEvent.Message("You already sent this person a request"), awaitItem())
            }
            assertEquals(Relation.NONE, vm.state.value.results[0].relation)
        }

    @Test
    fun `a new code replaces the old one`() =
        runTest {
            val vm = AddFriendViewModel(repo)

            vm.events.test {
                vm.regenerateCode()
                assertTrue((awaitItem() as SocialEvent.Message).text.contains("old one no longer works"))
            }
            assertEquals("NEWCODE22", vm.state.value.code!!.code)
            assertFalse(vm.state.value.regenerating)
        }

    @Test
    fun `a typed code is cleaned up and used`() =
        runTest {
            val vm = AddFriendViewModel(repo)
            vm.setCodeInput("  abcd2345 ")

            vm.events.test {
                vm.redeemTyped()
                assertEquals(SocialEvent.Message("You are now friends with Friend redeemed."), awaitItem())
            }
            assertEquals(listOf("ABCD2345"), repo.redeemed)
            assertEquals("", vm.state.value.codeInput)
            assertFalse(vm.state.value.redeeming)
        }

    @Test
    fun `a scanned link gives the code inside it`() =
        runTest {
            val vm = AddFriendViewModel(repo)

            vm.events.test {
                vm.onScanned("manproject://friend/zzzz2222")
                awaitItem()
            }
            assertEquals(listOf("ZZZZ2222"), repo.redeemed)
        }

    @Test
    fun `scanned text that is not a code is not sent to the server`() =
        runTest {
            val vm = AddFriendViewModel(repo)

            vm.events.test {
                vm.onScanned("https://evil.example/ABCD2345")
                assertEquals(SocialEvent.Message("That does not look like an invite code."), awaitItem())
            }
            assertTrue(repo.redeemed.isEmpty())
        }

    @Test
    fun `a code the server refuses keeps what was typed`() =
        runTest {
            repo.redeemResult = AppResult.Failure(ApiFailure.NotFound)
            val vm = AddFriendViewModel(repo)
            vm.setCodeInput("ABCD2345")

            vm.events.test {
                vm.redeemTyped()
                assertEquals(SocialEvent.Message(ApiFailure.NotFound.message), awaitItem())
            }
            assertEquals("ABCD2345", vm.state.value.codeInput)
            assertFalse(vm.state.value.redeeming)
        }
}

class PrivacyViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repo = FakeSocialRepository()

    @Test
    fun `settings and blocked people load`() =
        runTest {
            repo.privacy = loaded(Privacy(showFeed = false, showPrWeights = true, showOnLeaderboard = true))
            repo.blocked = loaded(listOf(friend("x")))

            val vm = PrivacyViewModel(repo)

            assertFalse(vm.state.value.privacy.showFeed)
            assertTrue(vm.state.value.privacy.showPrWeights)
            assertEquals(1, vm.state.value.blocked.size)
        }

    @Test
    fun `a switch is shown at once and saved`() =
        runTest {
            val vm = PrivacyViewModel(repo)

            vm.setShowPrWeights(true)
            vm.setShowOnLeaderboard(false)
            vm.setShowFeed(true) // already on: nothing to save

            assertEquals(
                listOf(
                    Privacy(showFeed = true, showPrWeights = true, showOnLeaderboard = true),
                    Privacy(showFeed = true, showPrWeights = true, showOnLeaderboard = false),
                ),
                repo.savedPrivacy,
            )
            assertTrue(vm.state.value.privacy.showPrWeights)
            assertFalse(vm.state.value.privacy.showOnLeaderboard)
            assertFalse(vm.state.value.saving)
        }

    @Test
    fun `when saving fails the old setting comes back with a message`() =
        runTest {
            repo.privacyResult = AppResult.Failure(ApiFailure.Offline)
            val vm = PrivacyViewModel(repo)

            vm.events.test {
                vm.setShowFeed(false)
                assertEquals(SocialEvent.Message(ApiFailure.Offline.message), awaitItem())
            }
            assertTrue(vm.state.value.privacy.showFeed)
        }

    @Test
    fun `unblocking removes the person from the list`() =
        runTest {
            repo.blocked = loaded(listOf(friend("x", "Xena"), friend("y")))
            val vm = PrivacyViewModel(repo)

            vm.events.test {
                vm.unblock(vm.state.value.blocked[0])
                assertEquals(SocialEvent.Message("Xena is unblocked."), awaitItem())
            }
            assertEquals(listOf("y"), vm.state.value.blocked.map { it.user.id })
            assertEquals(listOf("unblock:x"), repo.calls)
        }

    @Test
    fun `a settings error can be retried`() =
        runTest {
            repo.privacy = SocialLoad(null, ApiFailure.Offline, false)
            val vm = PrivacyViewModel(repo)
            assertEquals(ApiFailure.Offline.message, vm.state.value.error)

            repo.privacy = loaded(Privacy.DEFAULT)
            vm.load()

            assertNull(vm.state.value.error)
        }
}

class DuelsViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repo = FakeSocialRepository()

    @Test
    fun `duels are grouped by what the user has to do`() =
        runTest {
            repo.duels =
                loaded(
                    listOf(
                        duel("answer", DuelStatus.PENDING, youAreChallenger = false),
                        duel("running", DuelStatus.ACTIVE, mine = 2, theirs = 1),
                        duel("sent", DuelStatus.PENDING, youAreChallenger = true),
                        duel("done", DuelStatus.FINISHED, mine = 3, theirs = 1, winnerId = "me"),
                        duel("no", DuelStatus.DECLINED),
                    ),
                )

            val s = DuelsViewModel(repo).state.value

            assertEquals(listOf("answer"), s.waitingForMe.map { it.id })
            assertEquals(listOf("running"), s.active.map { it.id })
            assertEquals(listOf("sent"), s.sent.map { it.id })
            assertEquals(listOf("done", "no"), s.past.map { it.id })
        }

    @Test
    fun `no duels is an empty state and an error is not`() =
        runTest {
            assertTrue(DuelsViewModel(repo).state.value.isEmpty)

            repo.duels = offlineWith<List<com.example.fitapp.feature.social.domain.Duel>>(null)
            val failed = DuelsViewModel(repo)
            assertFalse(failed.state.value.isEmpty)
            assertEquals(ApiFailure.Offline.message, failed.state.value.error)
        }

    @Test
    fun `a challenge needs a friend picked and is sent with the chosen metric`() =
        runTest {
            repo.friends = loaded(listOf(friend("a")))
            val vm = DuelsViewModel(repo)

            vm.startCreating()
            assertFalse(vm.state.value.creating!!.ready)
            vm.sendChallenge()
            assertTrue(repo.createdDuels.isEmpty())

            vm.chooseOpponent("a")
            vm.chooseMetric(DuelMetric.LOCKINS)
            vm.events.test {
                vm.sendChallenge()
                assertEquals(SocialEvent.Message("Challenge sent to Rival d1."), awaitItem())
            }

            assertEquals(listOf("a" to DuelMetric.LOCKINS), repo.createdDuels)
            assertNull(vm.state.value.creating)
        }

    @Test
    fun `a refused challenge keeps the dialog open with the reason`() =
        runTest {
            repo.friends = loaded(listOf(friend("a")))
            repo.duelResult = AppResult.Failure(ApiFailure.Invalid("Too many duels at once (5 open duels each)"))
            val vm = DuelsViewModel(repo)
            vm.startCreating()
            vm.chooseOpponent("a")

            vm.events.test {
                vm.sendChallenge()
                assertEquals(SocialEvent.Message("Too many duels at once (5 open duels each)"), awaitItem())
            }
            assertTrue(vm.state.value.creating!!.ready)
            assertFalse(vm.state.value.busy)
        }

    @Test
    fun `accept, decline and withdraw call the server and refresh`() =
        runTest {
            repo.duels =
                loaded(listOf(duel("a", DuelStatus.PENDING, youAreChallenger = false), duel("b", DuelStatus.PENDING, youAreChallenger = true)))
            val vm = DuelsViewModel(repo)

            vm.accept(vm.state.value.waitingForMe.single())
            vm.decline(vm.state.value.waitingForMe.single())
            vm.withdraw(vm.state.value.sent.single())

            assertEquals(listOf("acceptDuel:a", "declineDuel:a", "cancelDuel:b"), repo.calls)
            assertFalse(vm.state.value.busy)
        }

    @Test
    fun `the dialog can be cancelled`() =
        runTest {
            val vm = DuelsViewModel(repo)
            vm.startCreating()

            vm.cancelCreating()

            assertNull(vm.state.value.creating)
        }
}
