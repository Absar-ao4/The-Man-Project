package com.example.fitapp.fakes

import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.lockin.domain.DoNotDisturb
import com.example.fitapp.feature.lockin.domain.LockinDay
import com.example.fitapp.feature.lockin.domain.LockinLine
import com.example.fitapp.feature.lockin.domain.LockinMethod
import com.example.fitapp.feature.lockin.domain.LockinPreferences
import com.example.fitapp.feature.lockin.domain.LockinProgram
import com.example.fitapp.feature.lockin.domain.LockinRepository
import com.example.fitapp.feature.profile.domain.FitnessLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

fun lockinLine(
    exerciseId: String? = "bench",
    name: String = "Barbell Bench Press",
    sets: Int = 3,
    repMin: Int = 8,
    repMax: Int = 10,
    rest: Int = 60,
    method: LockinMethod = LockinMethod.STRAIGHT,
    drops: Int = 0,
) = LockinLine(exerciseId, name, "CHEST", sets, repMin, repMax, rest, 8.0, method, null, drops, listOf("Brace your core.", "Control the way down."))

fun lockinProgram(
    id: String = "program-1",
    name: String = "German Volume Training",
    level: FitnessLevel = FitnessLevel.INTERMEDIATE,
    days: List<LockinDay> = listOf(LockinDay(0, "Chest and Back", "Volume", 60, listOf(lockinLine()))),
) = LockinProgram(id, name, "Ten sets of ten.", "Same weight every set.", level, 60, days.size.coerceAtLeast(1), days)

class FakeLockinRepository : LockinRepository {
    val programs = MutableStateFlow<List<LockinProgram>>(emptyList())
    var refreshResult: AppResult<Unit> = AppResult.Success(Unit)
    var refreshCalls = 0
        private set

    override fun observePrograms(): Flow<List<LockinProgram>> = programs

    override fun observeProgram(id: String): Flow<LockinProgram?> = programs.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun refresh(): AppResult<Unit> {
        refreshCalls++
        return refreshResult
    }
}

class FakeLockinPreferences(
    warned: Boolean = false,
    dnd: Boolean = false,
    pin: Boolean = false,
) : LockinPreferences {
    private val warning = MutableStateFlow(warned)
    private val silence = MutableStateFlow(dnd)
    private val pinned = MutableStateFlow(pin)

    override val warningAcknowledged: Flow<Boolean> = warning
    override val useDoNotDisturb: Flow<Boolean> = silence
    override val pinScreen: Flow<Boolean> = pinned

    override suspend fun acknowledgeWarning() {
        warning.value = true
    }

    override suspend fun setUseDoNotDisturb(enabled: Boolean) {
        silence.value = enabled
    }

    override suspend fun setPinScreen(enabled: Boolean) {
        pinned.value = enabled
    }
}

class FakeDoNotDisturb(
    private var access: Boolean = true,
) : DoNotDisturb {
    var enables = 0
        private set
    var restores = 0
        private set

    override fun hasAccess(): Boolean = access

    override suspend fun enable() {
        enables++
    }

    override suspend fun restore() {
        restores++
    }
}

/** A clock the test moves by hand. */
class MutableClock(
    var nowMillis: Long,
) : Clock() {
    override fun getZone(): ZoneId = ZoneId.of("UTC")

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = Instant.ofEpochMilli(nowMillis)
}
