package com.example.fitapp.feature.generator.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.fitapp.core.datastore.SettingsDataStore
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.network.AppJson
import com.example.fitapp.core.network.GeneratorApi
import com.example.fitapp.core.network.dto.PlanAnswersDto
import com.example.fitapp.core.network.safeApiCall
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.generator.domain.Explanation
import com.example.fitapp.feature.generator.domain.GeneratedPlan
import com.example.fitapp.feature.generator.domain.PlanAnswers
import com.example.fitapp.feature.generator.domain.PlanGeneratorRepository
import com.example.fitapp.feature.workout.domain.Routine
import com.example.fitapp.feature.workout.domain.RoutineExercise
import com.example.fitapp.feature.workout.domain.RoutineRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Where the generator keeps its small bits of state on this device. */
interface GeneratorStore {
    suspend fun answersJson(): String?

    suspend fun saveAnswersJson(json: String)

    /** The routines created from the last saved plan, so the next save can replace them. */
    suspend fun routineIds(): Set<String>

    suspend fun saveRoutineIds(ids: Set<String>)
}

@Singleton
class DataStoreGeneratorStore
    @Inject
    constructor(
        @SettingsDataStore private val store: DataStore<Preferences>,
    ) : GeneratorStore {
        override suspend fun answersJson(): String? = store.data.first()[ANSWERS]

        override suspend fun saveAnswersJson(json: String) {
            store.edit { it[ANSWERS] = json }
        }

        override suspend fun routineIds(): Set<String> = store.data.first()[ROUTINES].orEmpty()

        override suspend fun saveRoutineIds(ids: Set<String>) {
            store.edit { it[ROUTINES] = ids }
        }

        private companion object {
            val ANSWERS = stringPreferencesKey("generator_answers")
            val ROUTINES = stringSetPreferencesKey("generator_routine_ids")
        }
    }

@Singleton
class PlanGeneratorRepositoryImpl
    @Inject
    constructor(
        private val api: GeneratorApi,
        private val store: GeneratorStore,
        private val routines: RoutineRepository,
        private val clock: Clock,
    ) : PlanGeneratorRepository {
        override suspend fun savedAnswers(): PlanAnswers? {
            store.answersJson()?.let { json ->
                runCatching { AppJson.decodeFromString(PlanAnswersDto.serializer(), json).toDomain() }.getOrNull()?.let { return it }
            }
            // A new device: the server remembers the last questionnaire. Offline or none yet is fine.
            return (safeApiCall { api.savedAnswers() } as? AppResult.Success)?.value?.toDomain()
        }

        override suspend fun generate(answers: PlanAnswers): AppResult<GeneratedPlan> =
            when (val result = safeApiCall { api.generate(answers.toDto()) }) {
                is AppResult.Failure -> result
                is AppResult.Success -> {
                    store.saveAnswersJson(AppJson.encodeToString(PlanAnswersDto.serializer(), answers.toDto()))
                    AppResult.Success(result.value.toDomain())
                }
            }

        override suspend fun explain(answers: PlanAnswers): AppResult<Explanation> =
            when (val result = safeApiCall { api.explain(answers.toDto()) }) {
                is AppResult.Success ->
                    AppResult.Success(Explanation(result.value.explanation, result.value.suggestions, result.value.explainsLeftToday))
                is AppResult.Failure ->
                    AppResult.Failure(
                        when (result.failure) {
                            // Here a 429 is the daily limit, not "too many attempts", and a 503 is "not set up or busy".
                            is ApiFailure.RateLimited -> ApiFailure.Unknown("You have used today's AI explanations. Try again tomorrow.")
                            is ApiFailure.Server -> ApiFailure.Server("The AI explanation is not available right now.")
                            else -> result.failure
                        },
                    )
            }

        override suspend fun saveAsRoutines(
            plan: GeneratedPlan,
            replacePrevious: Boolean,
        ): AppResult<Int> {
            if (replacePrevious) {
                store.routineIds().forEach { routines.delete(it) }
                store.saveRoutineIds(emptySet())
            }
            val saved = mutableSetOf<String>()
            for (day in plan.days.filter { it.exercises.isNotEmpty() }) {
                val routine =
                    Routine(
                        id = UUID.randomUUID().toString(),
                        name = "${plan.name}: ${day.name}".take(MAX_NAME),
                        notes = day.focus,
                        sourcePlanDayId = null,
                        exercises =
                            day.exercises.mapIndexed { index, e ->
                                RoutineExercise(
                                    id = UUID.randomUUID().toString(),
                                    exerciseId = e.exerciseId,
                                    exerciseName = e.exerciseName,
                                    orderIndex = index,
                                    targetSets = e.sets,
                                    repMin = e.repMin,
                                    repMax = e.repMax,
                                    restSeconds = e.restSeconds,
                                )
                            },
                        updatedAt = Instant.ofEpochMilli(clock.millis()),
                    )
                when (val result = routines.save(routine)) {
                    is AppResult.Success -> saved += routine.id
                    is AppResult.Failure -> {
                        store.saveRoutineIds(saved)
                        return result
                    }
                }
            }
            store.saveRoutineIds(saved)
            return AppResult.Success(saved.size)
        }

        private companion object {
            const val MAX_NAME = 100
        }
    }
