package com.example.fitapp.navigation

import com.example.fitapp.MainDispatcherRule
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.fakes.FakeProfileRepository
import com.example.fitapp.fakes.FakeTokenStore
import com.example.fitapp.feature.profile.domain.ActivityLevel
import com.example.fitapp.feature.profile.domain.FitnessLevel
import com.example.fitapp.feature.profile.domain.Goal
import com.example.fitapp.feature.profile.domain.Sex
import com.example.fitapp.feature.profile.domain.Units
import com.example.fitapp.feature.profile.domain.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repo = FakeProfileRepository()

    private fun profile(complete: Boolean) =
        UserProfile(
            id = "p",
            name = "Alice",
            age = if (complete) 30 else null,
            sex = if (complete) Sex.MALE else null,
            heightCm = if (complete) 180.0 else null,
            weightKg = if (complete) 80.0 else null,
            fitnessLevel = FitnessLevel.BEGINNER,
            activityLevel = ActivityLevel.MODERATE,
            goal = Goal.MAINTENANCE,
            timezone = "UTC",
            currency = "USD",
            units = Units.METRIC,
        )

    /** Keeps the WhileSubscribed state flow active for the duration of the test. */
    private fun kotlinx.coroutines.test.TestScope.watch(vm: RootViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
    }

    @Test
    fun `no session means logged out`() =
        runTest {
            val vm = RootViewModel(FakeTokenStore(loggedIn = false), repo)
            watch(vm)
            assertEquals(RootState.LoggedOut, vm.state.value)
            assertEquals(0, repo.refreshCalls)
        }

    @Test
    fun `complete profile goes straight to the app`() =
        runTest {
            repo.profileAfterRefresh = profile(complete = true)
            val vm = RootViewModel(FakeTokenStore(loggedIn = true), repo)
            watch(vm)

            assertEquals(RootState.Ready, vm.state.value)
            assertEquals(1, repo.refreshCalls)
            assertEquals(1, repo.syncCalls)
        }

    @Test
    fun `incomplete profile needs onboarding`() =
        runTest {
            repo.profileAfterRefresh = profile(complete = false)
            val vm = RootViewModel(FakeTokenStore(loggedIn = true), repo)
            watch(vm)
            assertEquals(RootState.NeedsOnboarding, vm.state.value)
        }

    @Test
    fun `refresh failure with nothing cached shows an error that retry can fix`() =
        runTest {
            repo.refreshResult = AppResult.Failure(ApiFailure.Offline)
            val vm = RootViewModel(FakeTokenStore(loggedIn = true), repo)
            watch(vm)
            assertTrue(vm.state.value is RootState.Error)

            repo.refreshResult = AppResult.Success(Unit)
            repo.profileAfterRefresh = profile(complete = true)
            vm.retry()
            assertEquals(RootState.Ready, vm.state.value)
        }

    @Test
    fun `refresh failure with cached data still opens the app offline`() =
        runTest {
            repo.profile.value = profile(complete = true)
            repo.refreshResult = AppResult.Failure(ApiFailure.Offline)
            val vm = RootViewModel(FakeTokenStore(loggedIn = true), repo)
            watch(vm)
            assertEquals(RootState.Ready, vm.state.value)
        }

    @Test
    fun `logging out returns to logged out`() =
        runTest {
            val tokens = FakeTokenStore(loggedIn = true)
            repo.profileAfterRefresh = profile(complete = true)
            val vm = RootViewModel(tokens, repo)
            watch(vm)
            assertEquals(RootState.Ready, vm.state.value)

            tokens.clear()
            assertEquals(RootState.LoggedOut, vm.state.value)
        }
}
