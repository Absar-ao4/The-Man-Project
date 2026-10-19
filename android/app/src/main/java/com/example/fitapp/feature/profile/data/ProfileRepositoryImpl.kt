package com.example.fitapp.feature.profile.data

import com.example.fitapp.core.database.DailyTargetDao
import com.example.fitapp.core.database.DailyTargetEntity
import com.example.fitapp.core.database.ProfileDao
import com.example.fitapp.core.database.SyncStatus
import com.example.fitapp.core.datastore.TokenStore
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.network.ProfileApi
import com.example.fitapp.core.network.safeApiCall
import com.example.fitapp.core.sync.SyncTrigger
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.core.util.map
import com.example.fitapp.feature.profile.domain.ProfileRepository
import com.example.fitapp.feature.profile.domain.SuggestedTargets
import com.example.fitapp.feature.profile.domain.TargetSuggestionInput
import com.example.fitapp.feature.profile.domain.Targets
import com.example.fitapp.feature.profile.domain.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepositoryImpl
    @Inject
    constructor(
        private val api: ProfileApi,
        private val profileDao: ProfileDao,
        private val targetDao: DailyTargetDao,
        private val tokens: TokenStore,
        private val clock: Clock,
        private val sync: SyncTrigger,
    ) : ProfileRepository {
        override fun observeProfile(): Flow<UserProfile?> =
            tokens.userId.flatMapLatest { id ->
                if (id == null) flowOf(null) else profileDao.observe(id).map { it?.toDomain() }
            }

        override fun observeTargets(): Flow<Targets?> =
            tokens.userId.flatMapLatest { id ->
                if (id == null) {
                    flowOf(null)
                } else {
                    profileDao.observe(id)
                        .map { it?.timezone ?: DEFAULT_TIMEZONE }
                        .distinctUntilChanged()
                        .flatMapLatest { tz -> targetDao.observeCurrent(id, today(tz).toString()) }
                        .map { it?.toDomain() }
                }
            }

        /** The profile and targets come down with everything else in a sync. */
        override suspend fun refresh(): AppResult<Unit> = sync.syncNow()

        override suspend fun saveProfile(profile: UserProfile): AppResult<Unit> {
            val userId = tokens.currentUserId() ?: return AppResult.Failure(ApiFailure.Unauthorized())
            val existing = profileDao.get(userId)
            val entity =
                profile.toEntity(
                    userId = userId,
                    updatedAt = clock.millis(),
                    version = existing?.version ?: 0,
                    syncStatus = SyncStatus.PENDING,
                )
            profileDao.upsert(entity)
            sync.requestSync()
            return AppResult.Success(Unit)
        }

        override suspend fun saveTargets(targets: Targets): AppResult<Unit> {
            val userId = tokens.currentUserId() ?: return AppResult.Failure(ApiFailure.Unauthorized())
            val date = targets.effectiveFrom.toString()
            val existing = targetDao.findByDate(userId, date)
            val entity =
                DailyTargetEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    userId = userId,
                    calories = targets.calories,
                    proteinG = targets.proteinG,
                    carbsG = targets.carbsG,
                    fatG = targets.fatG,
                    waterGlasses = targets.waterGlasses,
                    effectiveFrom = date,
                    updatedAt = clock.millis(),
                    version = existing?.version ?: 0,
                    syncStatus = SyncStatus.PENDING,
                )
            targetDao.upsert(entity)
            sync.requestSync()
            return AppResult.Success(Unit)
        }

        /** A calculation on the server, not synced data. */
        override suspend fun suggestTargets(input: TargetSuggestionInput): AppResult<SuggestedTargets> =
            safeApiCall { api.suggestTargets(input.toRequest()) }.map { it.toDomain() }

        override suspend fun syncPending() = sync.requestSync()

        private fun today(timezone: String): LocalDate =
            LocalDate.now(clock.withZone(runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.of(DEFAULT_TIMEZONE))))

        private companion object {
            const val DEFAULT_TIMEZONE = "UTC"
        }
    }
