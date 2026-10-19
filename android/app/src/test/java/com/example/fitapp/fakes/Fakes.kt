package com.example.fitapp.fakes

import com.example.fitapp.core.datastore.TokenStore
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.auth.domain.AuthRepository
import com.example.fitapp.feature.profile.domain.DeviceInfo
import com.example.fitapp.feature.profile.domain.ProfileRepository
import com.example.fitapp.feature.profile.domain.SuggestedTargets
import com.example.fitapp.feature.profile.domain.TargetSuggestionInput
import com.example.fitapp.feature.profile.domain.Targets
import com.example.fitapp.feature.profile.domain.UserProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class FakeTokenStore(
    loggedIn: Boolean = false,
) : TokenStore {
    private val tokens = MutableStateFlow<Triple<String, String, String>?>(if (loggedIn) Triple("access", "refresh", "user-1") else null)

    override val isLoggedIn: Flow<Boolean> = tokens.map { it != null }
    override val userId: Flow<String?> = tokens.map { it?.third }

    var clearCalls = 0
        private set

    override suspend fun accessToken(): String? = tokens.value?.first

    override suspend fun refreshToken(): String? = tokens.value?.second

    override suspend fun currentUserId(): String? = tokens.value?.third

    override suspend fun save(
        accessToken: String,
        refreshToken: String,
        userId: String,
    ) {
        tokens.value = Triple(accessToken, refreshToken, userId)
    }

    override suspend fun clear() {
        clearCalls++
        tokens.value = null
    }

    fun logIn() {
        tokens.value = Triple("access", "refresh", "user-1")
    }
}

class FakeAuthRepository : AuthRepository {
    var loginResult: AppResult<Unit> = AppResult.Success(Unit)
    var registerResult: AppResult<Unit> = AppResult.Success(Unit)

    /** When set, login/register suspend until completed, so tests can observe the Submitting state. */
    var gate: CompletableDeferred<Unit>? = null

    var loginCalls = 0
        private set
    var registerCalls = 0
        private set
    var lastLogin: Pair<String, String>? = null
        private set

    override suspend fun register(
        email: String,
        password: String,
        username: String,
        name: String,
    ): AppResult<Unit> {
        registerCalls++
        gate?.await()
        return registerResult
    }

    override suspend fun login(
        email: String,
        password: String,
    ): AppResult<Unit> {
        loginCalls++
        lastLogin = email to password
        gate?.await()
        return loginResult
    }

    override suspend fun logout() = Unit
}

class FakeProfileRepository : ProfileRepository {
    val profile = MutableStateFlow<UserProfile?>(null)
    val targets = MutableStateFlow<Targets?>(null)

    var refreshResult: AppResult<Unit> = AppResult.Success(Unit)
    var profileAfterRefresh: UserProfile? = null
    var suggestResult: AppResult<SuggestedTargets> =
        AppResult.Success(SuggestedTargets(1780, 2759, 2759, 128, 345, 77, 12))
    var saveTargetsResult: AppResult<Unit> = AppResult.Success(Unit)
    var saveProfileResult: AppResult<Unit> = AppResult.Success(Unit)

    /** Order of writes, e.g. ["targets", "profile"]. */
    val writes = mutableListOf<String>()
    var savedProfile: UserProfile? = null
        private set
    var savedTargets: Targets? = null
        private set
    var refreshCalls = 0
        private set
    var syncCalls = 0
        private set
    var lastSuggestionInput: TargetSuggestionInput? = null
        private set

    override fun observeProfile(): Flow<UserProfile?> = profile

    override fun observeTargets(): Flow<Targets?> = targets

    override suspend fun refresh(): AppResult<Unit> {
        refreshCalls++
        profileAfterRefresh?.let { profile.value = it }
        return refreshResult
    }

    override suspend fun saveProfile(profile: UserProfile): AppResult<Unit> {
        writes += "profile"
        savedProfile = profile
        if (saveProfileResult is AppResult.Success) this.profile.value = profile
        return saveProfileResult
    }

    override suspend fun saveTargets(targets: Targets): AppResult<Unit> {
        writes += "targets"
        savedTargets = targets
        if (saveTargetsResult is AppResult.Success) this.targets.value = targets
        return saveTargetsResult
    }

    override suspend fun suggestTargets(input: TargetSuggestionInput): AppResult<SuggestedTargets> {
        lastSuggestionInput = input
        return suggestResult
    }

    override suspend fun syncPending() {
        syncCalls++
    }
}

class FakeDeviceInfo(
    private val timezone: String = "Asia/Karachi",
    private val currency: String = "PKR",
    private val today: LocalDate = LocalDate.of(2026, 10, 17),
    private val hour: Int = 9,
) : DeviceInfo {
    override fun currentHour(): Int = hour

    override fun timezoneId(): String = timezone

    override fun currencyCode(): String = currency

    override fun today(): LocalDate = today
}

fun offline(): AppResult.Failure = AppResult.Failure(ApiFailure.Offline)
