package com.example.fitapp.feature

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fitapp.core.database.AppDatabase
import com.example.fitapp.core.database.SyncStatus
import com.example.fitapp.core.datastore.TokenStore
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.body.data.BodyRepositoryImpl
import com.example.fitapp.feature.body.domain.MeasurementType
import com.example.fitapp.feature.profile.domain.ActivityLevel
import com.example.fitapp.feature.profile.domain.FitnessLevel
import com.example.fitapp.feature.profile.domain.Goal
import com.example.fitapp.feature.profile.domain.ProfileRepository
import com.example.fitapp.feature.profile.domain.Sex
import com.example.fitapp.feature.profile.domain.SuggestedTargets
import com.example.fitapp.feature.profile.domain.TargetSuggestionInput
import com.example.fitapp.feature.profile.domain.Targets
import com.example.fitapp.feature.profile.domain.Units
import com.example.fitapp.feature.profile.domain.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private class BodyTokens(userId: String? = "u1") : TokenStore {
    private val id = MutableStateFlow(userId)
    override val isLoggedIn: Flow<Boolean> = id.map { it != null }
    override val userId: Flow<String?> = id

    override suspend fun accessToken(): String? = "a"

    override suspend fun refreshToken(): String? = "r"

    override suspend fun currentUserId(): String? = id.value

    override suspend fun save(
        accessToken: String,
        refreshToken: String,
        userId: String,
    ) = Unit

    override suspend fun clear() = Unit
}

private class StubProfiles(sex: Sex? = Sex.MALE) : ProfileRepository {
    private val profile =
        MutableStateFlow<UserProfile?>(
            UserProfile("p", "Sam", 30, sex, 180.0, 80.0, FitnessLevel.INTERMEDIATE, ActivityLevel.MODERATE, Goal.MAINTENANCE, "UTC", "USD", Units.METRIC),
        )

    override fun observeProfile(): Flow<UserProfile?> = profile

    override fun observeTargets(): Flow<Targets?> = MutableStateFlow(null)

    override suspend fun refresh(): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun saveProfile(profile: UserProfile): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun saveTargets(targets: Targets): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun suggestTargets(input: TargetSuggestionInput): AppResult<SuggestedTargets> = AppResult.Failure(ApiFailure.Offline)

    override suspend fun syncPending() = Unit
}

@RunWith(AndroidJUnit4::class)
class BodyRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var sync: RecordingSyncTrigger
    private lateinit var repo: BodyRepositoryImpl

    private val day = LocalDate.of(2026, 10, 17)
    private val clock = Clock.fixed(Instant.parse("2026-10-17T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        sync = RecordingSyncTrigger()
        repo = BodyRepositoryImpl(db.bodyDao(), StubProfiles(), BodyTokens(), clock, sync)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun aWeighInIsSavedPendingAndAsksForASync() =
        runTest {
            val result = repo.logWeight(day, 80.44)

            assertTrue(result is AppResult.Success)
            val saved = repo.observeWeights().first().single()
            assertEquals(day, saved.date)
            assertEquals(80.4, saved.weightKg, 0.0)
            assertEquals(SyncStatus.PENDING, db.bodyDao().weightOn("u1", day.toString())!!.syncStatus)
            assertEquals(1, sync.requests)
        }

    @Test
    fun loggingTheSameDayAgainReplacesTheRowAndKeepsItsId() =
        runTest {
            repo.logWeight(day, 80.0)
            val firstId = repo.observeWeights().first().single().id

            repo.logWeight(day, 79.5)

            val rows = repo.observeWeights().first()
            assertEquals(1, rows.size)
            assertEquals(firstId, rows.single().id)
            assertEquals(79.5, rows.single().weightKg, 0.0)
        }

    @Test
    fun weightsOutsideTheLimitsAreRefusedAndNothingIsSaved() =
        runTest {
            val low = repo.logWeight(day, 19.9)
            val high = repo.logWeight(day, 500.1)

            assertTrue(low is AppResult.Failure)
            assertTrue(high is AppResult.Failure)
            assertTrue(repo.observeWeights().first().isEmpty())
            assertEquals(0, sync.requests)
        }

    @Test
    fun aDeletedWeighInDisappearsButStaysAsATombstoneUntilSyncedAndCanBeRevived() =
        runTest {
            repo.logWeight(day, 80.0)
            val id = repo.observeWeights().first().single().id

            repo.deleteWeight(id)

            assertTrue(repo.observeWeights().first().isEmpty())
            val tombstone = db.bodyDao().weightById(id)!!
            assertNotNull(tombstone.deletedAt)
            assertEquals(SyncStatus.PENDING, tombstone.syncStatus)

            repo.logWeight(day, 81.0)

            val revived = repo.observeWeights().first().single()
            assertEquals(id, revived.id)
            assertEquals(81.0, revived.weightKg, 0.0)
            assertNull(db.bodyDao().weightById(id)!!.deletedAt)
        }

    @Test
    fun deletingSomethingThatIsNotThereDoesNothing() =
        runTest {
            repo.deleteWeight("nope")
            repo.deleteMeasurement("nope")

            assertEquals(0, sync.requests)
        }

    @Test
    fun measurementsAreOnePerBodyPartPerDay() =
        runTest {
            repo.logMeasurement(day, MeasurementType.WAIST, 85.0)
            repo.logMeasurement(day, MeasurementType.CHEST, 100.0)
            repo.logMeasurement(day, MeasurementType.WAIST, 84.2)

            val rows = repo.observeMeasurements().first()
            assertEquals(2, rows.size)
            assertEquals(84.2, rows.first { it.type == MeasurementType.WAIST }.valueCm, 0.0)
            assertEquals(100.0, rows.first { it.type == MeasurementType.CHEST }.valueCm, 0.0)
        }

    @Test
    fun measurementsOutsideTheLimitsAreRefused() =
        runTest {
            assertTrue(repo.logMeasurement(day, MeasurementType.NECK, 4.9) is AppResult.Failure)
            assertTrue(repo.logMeasurement(day, MeasurementType.NECK, 300.1) is AppResult.Failure)
            assertTrue(repo.observeMeasurements().first().isEmpty())
        }

    @Test
    fun theBodyCheckIsWorkedOutFromWhatIsOnThePhone() =
        runTest {
            (0..6).forEach { repo.logWeight(day.minusDays(it.toLong()), 80.0) }
            repo.logMeasurement(day, MeasurementType.WAIST, 85.0)
            repo.logMeasurement(day, MeasurementType.NECK, 38.0)

            val check = repo.observeBodyCheck().first()

            assertEquals(80.0, check.trendKg!!, 0.0)
            assertEquals(24.7, check.bmi!!, 0.0)
            assertNotNull(check.waistToHeight)
            assertNotNull(check.bodyFatPercent)
        }

    @Test
    fun anotherUsersRowsAreNotShown() =
        runTest {
            repo.logWeight(day, 80.0)
            val other = BodyRepositoryImpl(db.bodyDao(), StubProfiles(), BodyTokens("u2"), clock, sync)

            assertTrue(other.observeWeights().first().isEmpty())
        }
}
