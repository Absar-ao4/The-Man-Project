package com.example.fitapp.feature.workout.domain

import java.time.Instant

/**
 * On-device twin of the backend PrDetector (backend/.../workout/PrDetector.java), so a PR can be
 * celebrated mid-workout without the network. The server recomputes the same thing and wins.
 *
 * Rules: sets are processed in time order; the first qualifying set of an exercise, and the first
 * use of a rep count, only set a baseline. A set is a PR if its Epley e1RM beats the best so far or
 * its weight beats the best for the same rep count. Only weighted sets of 1 to 30 reps count.
 */
object PrDetector {
    const val MAX_REPS_FOR_PR = 30
    private const val EPSILON = 0.005

    data class SetPerformance(
        val setId: String,
        val exerciseId: String,
        val weightKg: Double,
        val reps: Int,
        val at: Instant,
        val order: Int,
    )

    data class Best(val value: Double, val setId: String, val at: Instant)

    data class ExerciseBests(val e1rm: Best, val repMax: Map<Int, Best>)

    data class Analysis(val prSetIds: Set<String>, val bests: Map<String, ExerciseBests>)

    fun epley(
        weightKg: Double,
        reps: Int,
    ): Double = weightKg * (1 + reps / 30.0)

    fun analyze(sets: List<SetPerformance>): Analysis {
        val prSetIds = mutableSetOf<String>()
        val bests = linkedMapOf<String, ExerciseBests>()

        val byExercise =
            sets
                .filter { it.weightKg > 0 && it.reps in 1..MAX_REPS_FOR_PR }
                .groupBy { it.exerciseId }

        for ((exerciseId, performances) in byExercise) {
            val ordered = performances.sortedWith(compareBy<SetPerformance> { it.at }.thenBy { it.order })
            var bestE1rm: Best? = null
            val repMax = sortedMapOf<Int, Best>()

            for (s in ordered) {
                var pr = false
                val e1rm = epley(s.weightKg, s.reps)
                val currentBest = bestE1rm
                if (currentBest == null) {
                    bestE1rm = Best(e1rm, s.setId, s.at)
                } else if (e1rm > currentBest.value + EPSILON) {
                    bestE1rm = Best(e1rm, s.setId, s.at)
                    pr = true
                }

                val previousAtReps = repMax[s.reps]
                if (previousAtReps == null) {
                    repMax[s.reps] = Best(s.weightKg, s.setId, s.at)
                } else if (s.weightKg > previousAtReps.value + EPSILON) {
                    repMax[s.reps] = Best(s.weightKg, s.setId, s.at)
                    pr = true
                }

                if (pr) prSetIds += s.setId
            }
            bests[exerciseId] = ExerciseBests(requireNotNull(bestE1rm), repMax)
        }
        return Analysis(prSetIds, bests)
    }
}
