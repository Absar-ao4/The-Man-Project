package com.example.fitapp.feature.gamification

import com.example.fitapp.MainDispatcherRule
import com.example.fitapp.core.network.dto.BadgeCelebrationDto
import com.example.fitapp.core.network.dto.CelebrationsDto
import com.example.fitapp.core.network.dto.LevelChangeDto
import com.example.fitapp.core.network.dto.XpEventCelebrationDto
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.gamification.domain.BadgeStatus
import com.example.fitapp.feature.gamification.domain.Celebration
import com.example.fitapp.feature.gamification.domain.CelebrationCenter
import com.example.fitapp.feature.gamification.domain.GamificationRepository
import com.example.fitapp.feature.gamification.domain.ProgressStats
import com.example.fitapp.feature.gamification.ui.CelebrationViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CelebrationViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class FakeGamification : GamificationRepository {
        var refreshes = 0
            private set

        override fun observeStats(): Flow<ProgressStats?> = MutableStateFlow(null)

        override fun observeBadges(): Flow<List<BadgeStatus>> = MutableStateFlow(emptyList())

        override suspend fun refresh(): AppResult<Unit> {
            refreshes++
            return AppResult.Success(Unit)
        }
    }

    private val center = CelebrationCenter()
    private val gamification = FakeGamification()

    private fun viewModel() = CelebrationViewModel(center, gamification)

    @Test
    fun nothingIsShownWhileTheQueueIsEmpty() =
        runTest {
            val vm = viewModel()
            val job = vm.current.onEach { }.launchIn(backgroundScope)

            assertNull(vm.current.value)
            assertEquals(0, gamification.refreshes)
            job.cancel()
        }

    @Test
    fun theOldestCelebrationIsShownAndDismissingMovesToTheNext() =
        runTest {
            val vm = viewModel()
            val job = vm.current.onEach { }.launchIn(backgroundScope)

            center.offer(
                CelebrationsDto(
                    xpEvents = listOf(XpEventCelebrationDto("WORKOUT", 50)),
                    levelChange = LevelChangeDto(1, 2, "Rookie"),
                    badges = listOf(BadgeCelebrationDto("first_workout", "First Rep")),
                ),
            )

            assertTrue(vm.current.value is Celebration.XpGained)
            vm.dismiss()
            assertTrue(vm.current.value is Celebration.LevelUp)
            vm.dismiss()
            assertTrue(vm.current.value is Celebration.BadgeUnlocked)
            vm.dismiss()
            assertNull(vm.current.value)
            job.cancel()
        }

    @Test
    fun newXpRefreshesTheCachedStatsOnce() =
        runTest {
            val vm = viewModel()
            val job = vm.current.onEach { }.launchIn(backgroundScope)

            center.offer(CelebrationsDto(xpEvents = listOf(XpEventCelebrationDto("WORKOUT", 50))))

            assertEquals(1, gamification.refreshes)
            job.cancel()
        }
}
