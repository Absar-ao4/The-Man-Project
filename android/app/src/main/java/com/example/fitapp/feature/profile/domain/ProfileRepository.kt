package com.example.fitapp.feature.profile.domain

import com.example.fitapp.core.util.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * Local-first: reads come from Room, writes land in Room immediately and are pushed to the server
 * when possible. A failed push leaves the row PENDING; [syncPending] retries it.
 */
interface ProfileRepository {
    fun observeProfile(): Flow<UserProfile?>

    /** The targets in force today (latest effective date that is not in the future). */
    fun observeTargets(): Flow<Targets?>

    /** Pulls profile and targets from the server into Room. "No targets yet" counts as success. */
    suspend fun refresh(): AppResult<Unit>

    suspend fun saveProfile(profile: UserProfile): AppResult<Unit>

    suspend fun saveTargets(targets: Targets): AppResult<Unit>

    suspend fun suggestTargets(input: TargetSuggestionInput): AppResult<SuggestedTargets>

    /** Pushes any rows still marked PENDING. Safe to call repeatedly. */
    suspend fun syncPending()
}
