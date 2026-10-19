package com.example.fitapp.feature.lockin.data

import com.example.fitapp.core.database.LockinDao
import com.example.fitapp.core.database.LockinProgramEntity
import com.example.fitapp.core.network.AppJson
import com.example.fitapp.core.network.LockinApi
import com.example.fitapp.core.network.dto.LockinDayDto
import com.example.fitapp.core.network.dto.LockinProgramDto
import com.example.fitapp.core.network.safeApiCall
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.lockin.domain.LockinDay
import com.example.fitapp.feature.lockin.domain.LockinLine
import com.example.fitapp.feature.lockin.domain.LockinMethod
import com.example.fitapp.feature.lockin.domain.LockinProgram
import com.example.fitapp.feature.lockin.domain.LockinRepository
import com.example.fitapp.feature.profile.domain.FitnessLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

private val DaysSerializer = ListSerializer(LockinDayDto.serializer())

@Singleton
class LockinRepositoryImpl
    @Inject
    constructor(
        private val api: LockinApi,
        private val dao: LockinDao,
        private val clock: Clock,
    ) : LockinRepository {
        override fun observePrograms(): Flow<List<LockinProgram>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

        override fun observeProgram(id: String): Flow<LockinProgram?> = dao.observe(id).map { it?.toDomain() }

        override suspend fun refresh(): AppResult<Unit> {
            val list =
                when (val result = safeApiCall { api.programs() }) {
                    is AppResult.Failure -> return result
                    is AppResult.Success -> result.value
                }
            list.forEachIndexed { index, summary ->
                val cached = dao.get(summary.id)
                // The days only come with the detail call. If that fails the older copy stays usable.
                val days =
                    when (val detail = safeApiCall { api.program(summary.id) }) {
                        is AppResult.Success -> AppJson.encodeToString(DaysSerializer, detail.value.days)
                        is AppResult.Failure -> cached?.daysJson ?: EMPTY_DAYS
                    }
                dao.upsert(summary.toEntity(index, days, clock.millis()))
            }
            dao.deleteAllExcept(list.map { it.id })
            return AppResult.Success(Unit)
        }

        private fun LockinProgramDto.toEntity(
            sortOrder: Int,
            daysJson: String,
            now: Long,
        ) = LockinProgramEntity(
            id = id,
            name = name,
            description = description,
            instructions = instructions,
            minLevel = minLevel,
            durationMinutes = durationMinutes,
            sortOrder = sortOrder,
            dayCount = dayCount,
            daysJson = daysJson,
            fetchedAt = now,
        )

        private companion object {
            const val EMPTY_DAYS = "[]"
        }
    }

internal fun LockinProgramEntity.toDomain(): LockinProgram {
    val days =
        runCatching { AppJson.decodeFromString(DaysSerializer, daysJson) }.getOrDefault(emptyList()).map { d ->
            LockinDay(
                index = d.dayIndex,
                name = d.name,
                focus = d.focus,
                estimatedMinutes = d.estimatedMinutes,
                lines =
                    d.exercises.map { l ->
                        LockinLine(
                            exerciseId = l.exerciseId,
                            exerciseName = l.exerciseName,
                            primaryMuscle = l.primaryMuscle,
                            sets = l.sets,
                            repMin = l.repMin,
                            repMax = l.repMax,
                            restSeconds = l.restSeconds,
                            rpe = l.rpe,
                            method = LockinMethod.fromName(l.method),
                            group = l.group,
                            drops = l.drops,
                            cues = l.cues,
                        )
                    },
            )
        }
    return LockinProgram(
        id = id,
        name = name,
        description = description,
        instructions = instructions,
        // An unknown level is treated as the strictest one, so a program is never opened by mistake.
        minLevel = FitnessLevel.entries.firstOrNull { it.name == minLevel } ?: FitnessLevel.ADVANCED,
        durationMinutes = durationMinutes,
        dayCount = dayCount,
        days = days,
    )
}
