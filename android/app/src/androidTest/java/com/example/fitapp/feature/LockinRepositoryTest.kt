package com.example.fitapp.feature

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fitapp.core.database.AppDatabase
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.network.LockinApi
import com.example.fitapp.core.network.dto.LockinDayDto
import com.example.fitapp.core.network.dto.LockinLineDto
import com.example.fitapp.core.network.dto.LockinProgramDto
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.lockin.data.LockinRepositoryImpl
import com.example.fitapp.feature.lockin.domain.LockinMethod
import com.example.fitapp.feature.profile.domain.FitnessLevel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private class ScriptedLockinApi : LockinApi {
    var online = true
    var detailsFail = false
    var programs: List<LockinProgramDto> = emptyList()

    private fun day(name: String) =
        LockinDayDto(
            0,
            name,
            "Volume",
            60,
            listOf(LockinLineDto("e1", "Barbell Bench Press", "CHEST", 10, 10, 10, 60, 7.5, "SUPERSET", "A", 0, listOf("Brace."))),
        )

    fun program(
        id: String,
        name: String = "Program $id",
        level: String = "INTERMEDIATE",
        withDays: Boolean = false,
    ) = LockinProgramDto(id, name, "About $name.", "How $name works.", level, 60, 1, true, null, if (withDays) listOf(day("Day for $name")) else emptyList())

    override suspend fun programs(): List<LockinProgramDto> {
        if (!online) throw IOException("offline")
        return programs.map { it.copy(days = emptyList()) }
    }

    override suspend fun program(id: String): LockinProgramDto {
        if (!online || detailsFail) throw IOException("offline")
        return programs.first { it.id == id }
    }
}

@RunWith(AndroidJUnit4::class)
class LockinRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var api: ScriptedLockinApi
    private lateinit var repo: LockinRepositoryImpl
    private val clock = Clock.fixed(Instant.parse("2026-03-02T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        api = ScriptedLockinApi()
        repo = LockinRepositoryImpl(api, db.lockinDao(), clock)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun nothingIsCachedBeforeTheFirstRefresh() =
        runTest {
            assertTrue(repo.observePrograms().first().isEmpty())
            assertNull(repo.observeProgram("a").first())
        }

    @Test
    fun refreshStoresEveryProgramWithItsDaysInServerOrder() =
        runTest {
            api.programs = listOf(api.program("b", "Second", withDays = true), api.program("a", "First", withDays = true))

            assertTrue(repo.refresh() is AppResult.Success)

            val programs = repo.observePrograms().first()
            assertEquals(listOf("b", "a"), programs.map { it.id })
            val first = programs.first()
            assertEquals(FitnessLevel.INTERMEDIATE, first.minLevel)
            assertTrue(first.hasDetail)
            val line = first.days.single().lines.single()
            assertEquals("e1", line.exerciseId)
            assertEquals(LockinMethod.SUPERSET, line.method)
            assertEquals(listOf("Brace."), line.cues)
        }

    @Test
    fun aProgramRemovedOnTheServerDisappears() =
        runTest {
            api.programs = listOf(api.program("a", withDays = true), api.program("b", withDays = true))
            repo.refresh()

            api.programs = listOf(api.program("a", withDays = true))
            repo.refresh()

            assertEquals(listOf("a"), repo.observePrograms().first().map { it.id })
        }

    @Test
    fun offlineKeepsWhatWasDownloadedAndReportsFailure() =
        runTest {
            api.programs = listOf(api.program("a", withDays = true))
            repo.refresh()
            api.online = false

            val result = repo.refresh()

            assertEquals(ApiFailure.Offline, (result as AppResult.Failure).failure)
            assertTrue(repo.observeProgram("a").first()!!.hasDetail)
        }

    @Test
    fun whenOnlyTheDetailCallFailsTheOlderDaysAreKept() =
        runTest {
            api.programs = listOf(api.program("a", name = "Old name", withDays = true))
            repo.refresh()

            api.programs = listOf(api.program("a", name = "New name", withDays = true))
            api.detailsFail = true
            repo.refresh()

            val program = repo.observeProgram("a").first()!!
            assertEquals("New name", program.name) // the list call still updated the summary
            assertTrue("the days from the earlier download survive", program.hasDetail)
        }

    @Test
    fun aFirstRefreshWhereDetailsFailGivesProgramsWithoutDays() =
        runTest {
            api.programs = listOf(api.program("a", withDays = true))
            api.detailsFail = true

            repo.refresh()

            assertFalse(repo.observeProgram("a").first()!!.hasDetail)
        }
}
