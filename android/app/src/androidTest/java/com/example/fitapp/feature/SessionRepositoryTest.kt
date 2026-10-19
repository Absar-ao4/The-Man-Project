package com.example.fitapp.feature

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fitapp.core.database.AppDatabase
import com.example.fitapp.core.database.RoutineEntity
import com.example.fitapp.core.database.RoutineExerciseEntity
import com.example.fitapp.core.database.SessionSetEntity
import com.example.fitapp.core.database.SyncStatus
import com.example.fitapp.core.database.WorkoutSessionEntity
import com.example.fitapp.core.datastore.TokenStore
import com.example.fitapp.core.notifications.RestTimerScheduler
import com.example.fitapp.feature.lockin.domain.LockinDay
import com.example.fitapp.feature.lockin.domain.LockinLine
import com.example.fitapp.feature.lockin.domain.LockinMethod
import com.example.fitapp.feature.lockin.domain.LockinProgram
import com.example.fitapp.feature.profile.domain.FitnessLevel
import com.example.fitapp.feature.workout.data.SessionRepositoryImpl
import com.example.fitapp.feature.workout.domain.Equipment
import com.example.fitapp.feature.workout.domain.Exercise
import com.example.fitapp.feature.workout.domain.Muscle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private class TestTokens(userId: String? = "u1") : TokenStore {
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

private class RecordingScheduler : RestTimerScheduler {
    var scheduledAt: Long? = null
    var scheduledFor: String? = null
    var cancels = 0

    override fun schedule(
        endsAtMillis: Long,
        exerciseName: String?,
    ) {
        scheduledAt = endsAtMillis
        scheduledFor = exerciseName
    }

    override fun cancel() {
        cancels++
    }
}

@RunWith(AndroidJUnit4::class)
class SessionRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var scheduler: RecordingScheduler
    private lateinit var sync: RecordingSyncTrigger
    private lateinit var repo: SessionRepositoryImpl

    private val now = Instant.parse("2026-03-10T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        scheduler = RecordingScheduler()
        sync = RecordingSyncTrigger()
        repo =
            SessionRepositoryImpl(
                dao = db.sessionDao(),
                exerciseDao = db.exerciseDao(),
                routineDao = db.routineDao(),
                tokens = TestTokens(),
                clock = clock,
                restScheduler = scheduler,
                sync = sync,
            )
    }

    @After
    fun tearDown() = db.close()

    private val bench = Exercise("bench", "Barbell Bench Press", "", Muscle.CHEST, emptyList(), Equipment.BARBELL, emptyList(), true, false)
    private val squat = Exercise("squat", "Barbell Back Squat", "", Muscle.QUADS, emptyList(), Equipment.BARBELL, emptyList(), true, false)

    /** A finished past workout: [kg] x [reps] on bench. */
    private suspend fun seedHistory(
        sessionId: String = "past",
        startedAt: Long = now.minusSeconds(7 * 86_400).toEpochMilli(),
        kg: Double = 80.0,
        reps: Int = 8,
    ) {
        db.sessionDao().upsert(
            WorkoutSessionEntity(
                id = sessionId,
                userId = "u1",
                name = "Past",
                startedAt = startedAt,
                endedAt = startedAt + 3_600_000,
                routineId = null,
                completed = true,
                updatedAt = startedAt,
                syncStatus = SyncStatus.SYNCED,
            ),
        )
        db.sessionDao().upsertSets(
            listOf(
                SessionSetEntity(
                    id = "$sessionId-set",
                    userId = "u1",
                    sessionId = sessionId,
                    exerciseId = "bench",
                    exerciseName = "Barbell Bench Press",
                    setIndex = 0,
                    weightKg = kg,
                    reps = reps,
                    completed = true,
                    completedAt = startedAt + 600_000,
                    updatedAt = startedAt,
                ),
            ),
        )
    }

    @Test
    fun startingFromARoutinePrefillsSetsFromLastTime() =
        runTest {
            seedHistory(kg = 77.5, reps = 8)
            db.routineDao().replaceRoutine(
                RoutineEntity("r1", "u1", "Push", "", null, 1L),
                listOf(RoutineExerciseEntity("re1", "u1", "r1", "bench", "Barbell Bench Press", 0, 3, 6, 10, 150, 1L)),
            )

            val id = repo.start("r1")
            val session = repo.observeSession(id).first()!!

            assertEquals("Push", session.name)
            assertEquals(3, session.sets.size)
            session.sets.forEach {
                assertEquals(77.5, it.weightKg, 0.0)
                assertEquals(8, it.reps)
                assertEquals(150, it.restSeconds)
                assertFalse(it.completed)
            }
        }

    @Test
    fun startingWhileAWorkoutIsActiveResumesIt() =
        runTest {
            val first = repo.start()
            val second = repo.start()
            assertEquals(first, second)
        }

    @Test
    fun completingASetStartsTheRestTimerAndSchedulesTheAlarm() =
        runTest {
            val id = repo.start()
            repo.addExercise(id, bench)
            val set = repo.observeSession(id).first()!!.sets.first()
            repo.updateSet(set.id, 80.0, 8)

            repo.toggleSetDone(set.id)

            val session = repo.observeSession(id).first()!!
            assertTrue(session.sets.first { it.id == set.id }.completed)
            val expectedEnd = now.toEpochMilli() + 120_000 // compound default rest
            assertEquals(expectedEnd, session.restEndsAt?.toEpochMilli())
            assertEquals(120, session.restTotalSeconds)
            assertEquals(expectedEnd, scheduler.scheduledAt)
        }

    @Test
    fun aSetNeedsAtLeastOneRepToBeCompleted() =
        runTest {
            val id = repo.start()
            repo.addExercise(id, bench)
            val set = repo.observeSession(id).first()!!.sets.first()
            repo.updateSet(set.id, 80.0, 0)

            repo.toggleSetDone(set.id)

            assertFalse(repo.observeSession(id).first()!!.sets.first { it.id == set.id }.completed)
            assertNull(scheduler.scheduledAt)
        }

    @Test
    fun undoingASetClearsItsCompletion() =
        runTest {
            val id = repo.start()
            repo.addExercise(id, bench)
            val set = repo.observeSession(id).first()!!.sets.first()
            repo.updateSet(set.id, 80.0, 8)

            repo.toggleSetDone(set.id)
            repo.toggleSetDone(set.id)

            val after = repo.observeSession(id).first()!!.sets.first { it.id == set.id }
            assertFalse(after.completed)
            assertNull(after.completedAt)
        }

    @Test
    fun beatingLastWeeksSetIsMarkedAsAPrLive() =
        runTest {
            seedHistory(kg = 80.0, reps = 8)
            val id = repo.start()
            repo.addExercise(id, bench)
            val set = repo.observeSession(id).first()!!.sets.first()

            repo.updateSet(set.id, 85.0, 8)
            repo.toggleSetDone(set.id)
            assertTrue(repo.observeSession(id).first()!!.sets.first { it.id == set.id }.isPr)

            // lowering the weight afterwards takes the PR back
            repo.updateSet(set.id, 70.0, 8)
            assertFalse(repo.observeSession(id).first()!!.sets.first { it.id == set.id }.isPr)
        }

    @Test
    fun theFirstEverSetOfAnExerciseIsNotAPr() =
        runTest {
            val id = repo.start()
            repo.addExercise(id, squat)
            val set = repo.observeSession(id).first()!!.sets.first()
            repo.updateSet(set.id, 100.0, 5)
            repo.toggleSetDone(set.id)
            assertFalse(repo.observeSession(id).first()!!.sets.first { it.id == set.id }.isPr)
        }

    @Test
    fun addingASetAppendsToItsExerciseBlockAndKeepsOrder() =
        runTest {
            val id = repo.start()
            repo.addExercise(id, bench)
            repo.addExercise(id, squat)

            repo.addSet(id, "bench")

            val sets = repo.observeSession(id).first()!!.sets
            assertEquals(7, sets.size)
            assertEquals((0..6).toList(), sets.map { it.setIndex })
            assertEquals(listOf("bench", "bench", "bench", "bench", "squat", "squat", "squat"), sets.map { it.exerciseId })
        }

    @Test
    fun removingASetAndAnExerciseReindexes() =
        runTest {
            val id = repo.start()
            repo.addExercise(id, bench)
            repo.addExercise(id, squat)
            val first = repo.observeSession(id).first()!!.sets.first()

            repo.removeSet(first.id)
            assertEquals((0..4).toList(), repo.observeSession(id).first()!!.sets.map { it.setIndex })

            repo.removeExercise(id, "bench")
            val remaining = repo.observeSession(id).first()!!.sets
            assertEquals(listOf("squat", "squat", "squat"), remaining.map { it.exerciseId })
            assertEquals(listOf(0, 1, 2), remaining.map { it.setIndex })
        }

    @Test
    fun finishingKeepsOnlyCompletedSetsAndQueuesTheUpload() =
        runTest {
            val id = repo.start()
            repo.addExercise(id, bench)
            val sets = repo.observeSession(id).first()!!.sets
            repo.updateSet(sets[0].id, 80.0, 8)
            repo.toggleSetDone(sets[0].id)

            val finished = repo.finish(id)!!

            assertNotNull(finished.endedAt)
            assertTrue(finished.completed)
            assertEquals(1, finished.sets.size) // the two untouched sets were dropped
            assertNull(finished.restEndsAt)
            assertEquals(1, sync.soonRequests) // the upload is requested, never awaited
            assertEquals(listOf(id), db.sessionDao().pendingFinished("u1").map { it.id })
            assertTrue(scheduler.cancels > 0)
            assertNull(db.sessionDao().getActive("u1"))
            assertEquals(1, repo.observeHistory().first().size)
        }

    @Test
    fun finishingWithNothingDoneDiscardsTheWorkout() =
        runTest {
            val id = repo.start()
            repo.addExercise(id, bench)

            assertNull(repo.finish(id))

            assertNull(db.sessionDao().get(id, "u1"))
            assertTrue(repo.observeHistory().first().isEmpty())
        }

    @Test
    fun discardingRemovesTheWorkoutAndItsPrs() =
        runTest {
            seedHistory(kg = 80.0, reps = 8)
            val id = repo.start()
            repo.addExercise(id, bench)
            repo.discard(id)

            assertNull(repo.observeActive().first())
            assertEquals(1, repo.observeHistory().first().size) // only the seeded past session
        }

    @Test
    fun deletingAFinishedWorkoutRemovesItFromHistoryAndRecalculatesRecords() =
        runTest {
            seedHistory("old", now.minusSeconds(14 * 86_400).toEpochMilli(), kg = 80.0, reps = 8)
            seedHistory("new", now.minusSeconds(7 * 86_400).toEpochMilli(), kg = 90.0, reps = 8)
            assertEquals(90.0 * (1 + 8 / 30.0), repo.observeRecords().first().single().e1rmKg, 0.01)

            repo.deleteSession("new")

            assertEquals(listOf("old"), repo.observeHistory().first().map { it.id })
            assertEquals(80.0 * (1 + 8 / 30.0), repo.observeRecords().first().single().e1rmKg, 0.01)
        }

    @Test
    fun deletingAFinishedWorkoutLeavesATombstoneForTheNextSync() =
        runTest {
            seedHistory("gone", kg = 80.0, reps = 8)

            repo.deleteSession("gone")

            val row = db.sessionDao().getById("gone")!!
            assertNotNull(row.deletedAt)
            assertEquals(SyncStatus.PENDING, row.syncStatus)
            assertEquals(1, sync.requests)
            assertEquals(listOf("gone"), db.sessionDao().pendingFinished("u1").map { it.id })
        }

    @Test
    fun previousPerformanceReturnsTheLastSessionsSetsInOrder() =
        runTest {
            seedHistory("a", now.minusSeconds(14 * 86_400).toEpochMilli(), kg = 70.0, reps = 10)
            seedHistory("b", now.minusSeconds(7 * 86_400).toEpochMilli(), kg = 75.0, reps = 8)

            val prev = repo.previousPerformance("bench", excludeSessionId = "none")

            assertEquals(1, prev.size)
            assertEquals(75.0, prev.single().weightKg, 0.0)
            assertEquals(8, prev.single().reps)
        }

    private fun lockinProgram(): LockinProgram {
        fun line(
            exerciseId: String?,
            name: String,
            sets: Int,
            repMin: Int,
            rest: Int,
        ) = LockinLine(exerciseId, name, "CHEST", sets, repMin, repMin, rest, 8.0, LockinMethod.STRAIGHT, null, 0, emptyList())
        val day =
            LockinDay(
                0,
                "Chest and Legs",
                "",
                60,
                listOf(
                    line("bench", "Barbell Bench Press", 3, 8, 60),
                    line(null, "Mystery Lift", 4, 10, 60), // not in the library: skipped
                    line("squat", "Barbell Back Squat", 2, 5, 150),
                ),
            )
        return LockinProgram("prog-1", "Heavy Day", "d", "i", FitnessLevel.INTERMEDIATE, 60, 1, listOf(day))
    }

    @Test
    fun startingALockinBuildsEverySetFromTheProgramDay() =
        runTest {
            seedHistory(kg = 77.5, reps = 6)

            val id = repo.startLockin(lockinProgram(), 0)
            val session = repo.observeSession(id).first()!!

            assertEquals("Heavy Day: Chest and Legs", session.name)
            assertTrue(session.isLockin)
            assertEquals("prog-1", session.lockinProgramId)
            assertEquals(5, session.sets.size) // 3 bench + 2 squat, the unknown lift is skipped
            val bench = session.sets.filter { it.exerciseId == "bench" }
            assertEquals(listOf(77.5, 77.5, 77.5), bench.map { it.weightKg })
            assertEquals(listOf(6, 6, 6), bench.map { it.reps }) // last time's reps
            assertEquals(listOf(60, 60, 60), bench.map { it.restSeconds })
            val squat = session.sets.filter { it.exerciseId == "squat" }
            assertEquals(listOf(5, 5), squat.map { it.reps }) // no history: the program's rep target
            assertEquals(listOf(150, 150), squat.map { it.restSeconds })
            assertEquals((0..4).toList(), session.sets.map { it.setIndex })
        }

    @Test
    fun startingALockinWhileAWorkoutIsActiveResumesIt() =
        runTest {
            val first = repo.start()
            val second = repo.startLockin(lockinProgram(), 0)
            assertEquals(first, second)
        }

    @Test
    fun quittingALockinKeepsFinishedSetsWithoutMarkingItCompleted() =
        runTest {
            val id = repo.startLockin(lockinProgram(), 0)
            val firstSet = repo.observeSession(id).first()!!.sets.first()
            repo.toggleSetDone(firstSet.id)

            val finished = repo.finishLockin(id, completed = false)!!

            assertNotNull(finished.endedAt)
            assertFalse(finished.completed)
            assertEquals(1, finished.sets.size) // the unfinished sets are dropped
            assertEquals(listOf(id), db.sessionDao().pendingFinished("u1").map { it.id })
            assertEquals(1, sync.soonRequests)
        }

    @Test
    fun finishingALockinNormallyMarksItCompleted() =
        runTest {
            val id = repo.startLockin(lockinProgram(), 0)
            repo.toggleSetDone(repo.observeSession(id).first()!!.sets.first().id)

            val finished = repo.finishLockin(id, completed = true)!!

            assertTrue(finished.completed)
            assertTrue(finished.isLockin)
        }

    @Test
    fun quittingALockinBeforeAnySetDropsTheSession() =
        runTest {
            val id = repo.startLockin(lockinProgram(), 0)

            assertNull(repo.finishLockin(id, completed = false))

            assertNull(db.sessionDao().get(id, "u1"))
            assertTrue(repo.observeHistory().first().isEmpty())
        }

    @Test
    fun theBestComboIsStoredOnTheSessionAndNeverNegative() =
        runTest {
            val id = repo.startLockin(lockinProgram(), 0)

            repo.setMaxCombo(id, 7)
            assertEquals(7, repo.observeSession(id).first()!!.maxCombo)

            repo.setMaxCombo(id, -3)
            assertEquals(0, repo.observeSession(id).first()!!.maxCombo)
        }

    @Test
    fun skippingRestCancelsTheAlarm() =
        runTest {
            val id = repo.start()
            repo.addExercise(id, bench)
            val set = repo.observeSession(id).first()!!.sets.first()
            repo.updateSet(set.id, 80.0, 8)
            repo.toggleSetDone(set.id)

            repo.setRest(id, null, 0)

            assertNull(repo.observeSession(id).first()!!.restEndsAt)
            assertTrue(scheduler.cancels > 0)
        }
}
