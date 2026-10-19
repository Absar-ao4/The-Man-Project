package com.example.fitapp.core

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fitapp.core.database.AppDatabase
import com.example.fitapp.core.database.ExerciseEntity
import com.example.fitapp.core.database.RoutineEntity
import com.example.fitapp.core.database.RoutineExerciseEntity
import com.example.fitapp.core.database.SessionSetEntity
import com.example.fitapp.core.database.SyncStatus
import com.example.fitapp.core.database.WorkoutSessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutDaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() = db.close()

    private fun exercise(
        id: String,
        name: String,
        muscle: String = "CHEST",
        equipment: String = "BARBELL",
        userId: String? = null,
        deletedAt: Long? = null,
    ) = ExerciseEntity(id, userId, name, "", muscle, "", equipment, "", true, 1L, deletedAt)

    private fun session(
        id: String,
        started: Long,
        ended: Long? = started + 1000,
        deletedAt: Long? = null,
    ) = WorkoutSessionEntity(
        id = id,
        userId = "u1",
        name = id,
        startedAt = started,
        endedAt = ended,
        routineId = null,
        updatedAt = started,
        deletedAt = deletedAt,
    )

    private fun set(
        id: String,
        sessionId: String,
        exerciseId: String = "bench",
        index: Int = 0,
        kg: Double = 80.0,
        reps: Int = 8,
        completed: Boolean = true,
        type: String = "NORMAL",
        flagged: Boolean = false,
    ) = SessionSetEntity(
        id = id,
        userId = "u1",
        sessionId = sessionId,
        exerciseId = exerciseId,
        exerciseName = exerciseId,
        setIndex = index,
        setType = type,
        weightKg = kg,
        reps = reps,
        completed = completed,
        completedAt = if (completed) 5_000L + index else null,
        flagged = flagged,
        updatedAt = 1L,
    )

    // ------------------------------------------------------------ exercises

    @Test
    fun exerciseSearchFiltersByTextMuscleAndEquipment() =
        runTest {
            db.exerciseDao().upsertAll(
                listOf(
                    exercise("bench", "Barbell Bench Press"),
                    exercise("fly", "Dumbbell Fly", equipment = "DUMBBELL"),
                    exercise("squat", "Barbell Back Squat", muscle = "QUADS"),
                    exercise("gone", "Deleted Press", deletedAt = 9L),
                ),
            )
            val dao = db.exerciseDao()

            assertEquals(3, dao.observe("", null, null).first().size)
            assertEquals(listOf("bench", "squat"), dao.observe("barbell", null, null).first().map { it.id })
            assertEquals(listOf("bench", "fly"), dao.observe("", "CHEST", null).first().map { it.id })
            assertEquals(listOf("fly"), dao.observe("", null, "DUMBBELL").first().map { it.id })
        }

    @Test
    fun exerciseSearchTreatsEscapedWildcardsLiterally() =
        runTest {
            db.exerciseDao().upsertAll(listOf(exercise("a", "100% Effort"), exercise("b", "Bench")))
            val dao = db.exerciseDao()
            assertEquals(listOf("a"), dao.observe("100\\%", null, null).first().map { it.id })
            // an escaped underscore is a literal underscore, not "any character"
            assertTrue(dao.observe("\\_", null, null).first().isEmpty())
        }

    // ------------------------------------------------------------ routines

    @Test
    fun replacingARoutineSwapsItsExercises() =
        runTest {
            val dao = db.routineDao()
            val routine = RoutineEntity("r1", "u1", "Push", "", null, 1L, syncStatus = SyncStatus.PENDING)
            fun row(
                id: String,
                order: Int,
            ) = RoutineExerciseEntity(id, "u1", "r1", "bench", "Bench", order, 3, 6, 10, 90, 1L)

            dao.replaceRoutine(routine, listOf(row("a", 0), row("b", 1)))
            assertEquals(2, dao.exercisesOf("r1").size)

            dao.replaceRoutine(routine.copy(name = "Push v2"), listOf(row("c", 0)))
            assertEquals(listOf("c"), dao.exercisesOf("r1").map { it.id })
            assertEquals("Push v2", dao.get("r1", "u1")?.name)
            assertEquals(1, dao.pending("u1").size)
        }

    @Test
    fun routinesAreScopedToTheirUser() =
        runTest {
            val dao = db.routineDao()
            dao.upsert(RoutineEntity("r1", "u1", "Mine", "", null, 1L))
            assertNull(dao.get("r1", "u2"))
            assertTrue(dao.observeAll("u2").first().isEmpty())
        }

    @Test
    fun softDeletedRoutinesAreHiddenButStillPending() =
        runTest {
            val dao = db.routineDao()
            dao.upsert(RoutineEntity("r1", "u1", "Gone", "", null, 1L, deletedAt = 5L, syncStatus = SyncStatus.PENDING))
            assertTrue(dao.observeAll("u1").first().isEmpty())
            assertEquals(1, dao.pending("u1").size)

            dao.hardDeleteWithChildren("r1")
            assertEquals(0, dao.pending("u1").size)
        }

    // ------------------------------------------------------------ sessions

    @Test
    fun activeSessionIsTheOneWithoutAnEndTime() =
        runTest {
            val dao = db.sessionDao()
            dao.upsert(session("done", 1000))
            assertNull(dao.getActive("u1"))

            dao.upsert(session("live", 5000, ended = null))
            assertEquals("live", dao.getActive("u1")?.id)
            assertEquals("live", dao.observeActive("u1").first()?.id)
            assertEquals(listOf("done"), dao.observeFinished("u1").first().map { it.id })
        }

    @Test
    fun prCandidatesExcludeWarmupsFlaggedUncompletedAndDeletedSessions() =
        runTest {
            val dao = db.sessionDao()
            dao.upsert(session("s1", 1000))
            dao.upsert(session("deleted", 2000, deletedAt = 9L))
            dao.upsertSets(
                listOf(
                    set("ok", "s1", index = 0),
                    set("warmup", "s1", index = 1, type = "WARMUP"),
                    set("flagged", "s1", index = 2, flagged = true),
                    set("undone", "s1", index = 3, completed = false),
                    set("deletedSession", "deleted", index = 0),
                ),
            )

            val ids = dao.prCandidates("u1", listOf("bench")).map { it.id }
            assertEquals(listOf("ok"), ids)
        }

    @Test
    fun previousSetsComeFromTheMostRecentOtherFinishedSession() =
        runTest {
            val dao = db.sessionDao()
            dao.upsert(session("old", 1000))
            dao.upsert(session("recent", 2000))
            dao.upsert(session("current", 3000, ended = null))
            dao.upsertSets(
                listOf(
                    set("o1", "old", kg = 60.0),
                    set("r1", "recent", index = 0, kg = 70.0),
                    set("r2", "recent", index = 1, kg = 72.5),
                    set("c1", "current", kg = 99.0),
                ),
            )

            val rows = dao.previousSets("u1", "bench", excludeSessionId = "current")
            assertEquals(listOf("r1", "r2", "o1"), rows.map { it.id })
            assertEquals("recent", rows.first().sessionId)
        }

    @Test
    fun allSetsSkipSessionsThatWereDeleted() =
        runTest {
            val dao = db.sessionDao()
            dao.upsert(session("live", 1000))
            dao.upsert(session("deleted", 2000, deletedAt = 9L))
            dao.upsertSets(listOf(set("a", "live"), set("b", "deleted")))

            assertEquals(listOf("a"), dao.observeAllSets("u1").first().map { it.id })
        }

    @Test
    fun pendingFinishedIgnoresTheActiveWorkout() =
        runTest {
            val dao = db.sessionDao()
            dao.upsert(session("live", 1000, ended = null).copy(syncStatus = SyncStatus.PENDING))
            dao.upsert(session("finished", 2000).copy(syncStatus = SyncStatus.PENDING))
            dao.upsert(session("synced", 3000))

            assertEquals(listOf("finished"), dao.pendingFinished("u1").map { it.id })
        }

    @Test
    fun hardDeleteWithSetsRemovesEverything() =
        runTest {
            val dao = db.sessionDao()
            dao.upsert(session("s1", 1000))
            dao.upsertSets(listOf(set("a", "s1"), set("b", "s1", index = 1)))

            dao.hardDeleteWithSets("s1")

            assertNull(dao.get("s1", "u1"))
            assertTrue(dao.sets("s1").isEmpty())
        }
}
