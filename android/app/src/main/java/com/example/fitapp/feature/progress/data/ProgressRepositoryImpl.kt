package com.example.fitapp.feature.progress.data

import com.example.fitapp.core.database.ProgressDao
import com.example.fitapp.core.database.SocialCacheEntity
import com.example.fitapp.core.database.SocialDao
import com.example.fitapp.core.datastore.TokenStore
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.network.AppJson
import com.example.fitapp.core.network.ProgressApi
import com.example.fitapp.core.network.dto.AchievementDto
import com.example.fitapp.core.network.dto.ActivityDto
import com.example.fitapp.core.network.dto.ProgressSummaryDto
import com.example.fitapp.core.network.safeApiCall
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.profile.domain.ProfileRepository
import com.example.fitapp.feature.progress.domain.Achievement
import com.example.fitapp.feature.progress.domain.ActivityDay
import com.example.fitapp.feature.progress.domain.ChartMath
import com.example.fitapp.feature.progress.domain.ExerciseChoice
import com.example.fitapp.feature.progress.domain.ProgressPeriod
import com.example.fitapp.feature.progress.domain.ProgressRepository
import com.example.fitapp.feature.progress.domain.ProgressSummary
import com.example.fitapp.feature.progress.domain.StrengthPoint
import com.example.fitapp.feature.social.domain.SocialLoad
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressRepositoryImpl
    @Inject
    constructor(
        private val api: ProgressApi,
        private val dao: ProgressDao,
        private val cache: SocialDao,
        private val tokens: TokenStore,
        private val profiles: ProfileRepository,
        private val clock: Clock,
    ) : ProgressRepository {
        // ------------------------------------------------------------------ from the server, with a saved copy

        override suspend fun summary(period: ProgressPeriod): SocialLoad<ProgressSummary> =
            load("progress:summary:${period.apiName}", ProgressSummaryDto.serializer(), { it.toDomain(period) }) { api.summary(period.apiName) }

        override suspend fun activity(days: Int): SocialLoad<List<ActivityDay>> =
            load("progress:activity:$days", ActivityDto.serializer(), { dto -> dto.days.map { it.toDomain() } }) { api.activity(days) }

        override suspend fun achievements(): SocialLoad<List<Achievement>> =
            load(
                key = "progress:achievements",
                serializer = kotlinx.serialization.builtins.ListSerializer(AchievementDto.serializer()),
                map = { list -> list.mapNotNull { it.toDomain() } },
            ) { api.achievements() }

        // ------------------------------------------------------------------ from the phone

        override fun observeWorkoutDays(days: Int): Flow<Map<LocalDate, Int>> =
            withUserAndZone { userId, zone ->
                val since = clock.millis() - (days + 1L) * MILLIS_PER_DAY
                dao.observeWorkoutEnds(userId, since).map { ChartMath.workoutsByDay(it, zone) }
            }

        override fun observeExercises(): Flow<List<ExerciseChoice>> =
            tokens.userId.flatMapLatest { userId ->
                if (userId == null) {
                    flowOf(emptyList())
                } else {
                    dao.observeExercisesWithHistory(userId).map { rows -> rows.map { ExerciseChoice(it.exerciseId, it.exerciseName, it.sets) } }
                }
            }

        override fun observeStrength(exerciseId: String): Flow<List<StrengthPoint>> =
            withUserAndZone { userId, zone ->
                dao.observeStrengthSets(userId, exerciseId).map { ChartMath.strengthPoints(it, zone) }
            }

        private fun <T> withUserAndZone(block: (String, ZoneId) -> Flow<T>): Flow<T> =
            combine(tokens.userId, profiles.observeProfile()) { userId, profile -> userId to zoneOf(profile?.timezone) }
                .flatMapLatest { (userId, zone) -> if (userId == null) emptyFlow() else block(userId, zone) }

        private fun zoneOf(id: String?): ZoneId = runCatching { ZoneId.of(id ?: "UTC") }.getOrDefault(ZoneOffset.UTC)

        private suspend fun <D, T> load(
            key: String,
            serializer: KSerializer<D>,
            map: (D) -> T,
            fetch: suspend () -> D,
        ): SocialLoad<T> {
            val userId = tokens.currentUserId() ?: return SocialLoad(null, ApiFailure.Unauthorized(), false)
            return when (val live = safeApiCall { fetch() }) {
                is AppResult.Success -> {
                    runCatching { cache.put(SocialCacheEntity(key, userId, AppJson.encodeToString(serializer, live.value), clock.millis())) }
                    SocialLoad(map(live.value), null, false)
                }
                is AppResult.Failure -> {
                    val saved =
                        runCatching { cache.get(userId, key)?.let { AppJson.decodeFromString(serializer, it.json) } }
                            .getOrNull()
                            ?.let(map)
                    SocialLoad(saved, live.failure, saved != null)
                }
            }
        }

        private companion object {
            const val MILLIS_PER_DAY = 86_400_000L
        }
    }
