package com.example.fitapp.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.example.fitapp.feature.coach.ui.CoachScreen
import com.example.fitapp.feature.generator.ui.PlanGeneratorScreen
import com.example.fitapp.feature.lockin.ui.LockinProgramScreen
import com.example.fitapp.feature.lockin.ui.LockinProgramsScreen
import com.example.fitapp.feature.lockin.ui.LockinSessionScreen
import com.example.fitapp.feature.lockin.ui.LockinSummaryScreen
import com.example.fitapp.feature.workout.ui.ExerciseDetailScreen
import com.example.fitapp.feature.workout.ui.HistoryDetailScreen
import com.example.fitapp.feature.workout.ui.HistoryScreen
import com.example.fitapp.feature.workout.ui.LibraryScreen
import com.example.fitapp.feature.workout.ui.PlanDetailScreen
import com.example.fitapp.feature.workout.ui.PlansScreen
import com.example.fitapp.feature.workout.ui.RecordsScreen
import com.example.fitapp.feature.workout.ui.RoutineEditorScreen
import com.example.fitapp.feature.workout.ui.RoutinesScreen
import com.example.fitapp.feature.workout.ui.SessionScreen
import com.example.fitapp.feature.workout.ui.WorkoutHubScreen

object WorkoutRoutes {
    /** The nested graph; the Workout tab navigates here. */
    const val GRAPH = "workout"
    const val HUB = "workout/hub"
    const val LIBRARY = "workout/library"
    const val EXERCISE = "workout/exercise/{id}"
    const val PLANS = "workout/plans"
    const val PLAN = "workout/plan/{id}"
    const val ROUTINES = "workout/routines"
    const val ROUTINE_EDITOR = "workout/routine/{id}"
    const val SESSION = "workout/session"
    const val HISTORY = "workout/history"
    const val HISTORY_DETAIL = "workout/history/{id}"
    const val RECORDS = "workout/records"
    const val GENERATOR = "workout/generator"
    const val COACH = "workout/coach"
    const val LOCKIN_PROGRAMS = "workout/lockin"
    const val LOCKIN_PROGRAM = "workout/lockin/program/{id}"
    const val LOCKIN_SESSION = "workout/lockin/session"
    const val LOCKIN_SUMMARY = "workout/lockin/summary/{id}"

    fun lockinProgram(id: String) = "workout/lockin/program/$id"

    fun lockinSummary(id: String) = "workout/lockin/summary/$id"

    fun exercise(id: String) = "workout/exercise/$id"

    fun plan(id: String) = "workout/plan/$id"

    fun routineEditor(id: String) = "workout/routine/$id"

    fun historyDetail(id: String) = "workout/history/$id"
}

fun NavGraphBuilder.workoutGraph(navController: NavHostController) {
    val back: () -> Unit = { navController.popBackStack() }

    navigation(startDestination = WorkoutRoutes.HUB, route = WorkoutRoutes.GRAPH) {
        composable(WorkoutRoutes.HUB) {
            WorkoutHubScreen(
                onOpenSession = { navController.navigate(WorkoutRoutes.SESSION) },
                onOpenLibrary = { navController.navigate(WorkoutRoutes.LIBRARY) },
                onOpenPlans = { navController.navigate(WorkoutRoutes.PLANS) },
                onOpenRoutines = { navController.navigate(WorkoutRoutes.ROUTINES) },
                onOpenHistory = { navController.navigate(WorkoutRoutes.HISTORY) },
                onOpenRecords = { navController.navigate(WorkoutRoutes.RECORDS) },
                onOpenLockin = { navController.navigate(WorkoutRoutes.LOCKIN_PROGRAMS) },
                onOpenLockinSession = { navController.navigate(WorkoutRoutes.LOCKIN_SESSION) },
                onOpenGenerator = { navController.navigate(WorkoutRoutes.GENERATOR) },
                onOpenCoach = { navController.navigate(WorkoutRoutes.COACH) },
            )
        }
        composable(WorkoutRoutes.COACH) { CoachScreen(onBack = back) }
        composable(WorkoutRoutes.GENERATOR) {
            PlanGeneratorScreen(onBack = back, onOpenRoutines = { navController.navigate(WorkoutRoutes.ROUTINES) })
        }
        composable(WorkoutRoutes.LOCKIN_PROGRAMS) {
            LockinProgramsScreen(onBack = back, onOpenProgram = { navController.navigate(WorkoutRoutes.lockinProgram(it)) })
        }
        composable(WorkoutRoutes.LOCKIN_PROGRAM) {
            LockinProgramScreen(
                onBack = back,
                onStarted = {
                    navController.navigate(WorkoutRoutes.LOCKIN_SESSION) {
                        popUpTo(WorkoutRoutes.LOCKIN_PROGRAM) { inclusive = true }
                    }
                },
            )
        }
        composable(WorkoutRoutes.LOCKIN_SESSION) {
            LockinSessionScreen(
                onFinished = { id ->
                    navController.navigate(WorkoutRoutes.lockinSummary(id)) {
                        popUpTo(WorkoutRoutes.LOCKIN_SESSION) { inclusive = true }
                    }
                },
                onClose = { navController.popBackStack(WorkoutRoutes.HUB, inclusive = false) },
            )
        }
        composable(WorkoutRoutes.LOCKIN_SUMMARY) {
            LockinSummaryScreen(onDone = { navController.popBackStack(WorkoutRoutes.HUB, inclusive = false) })
        }
        composable(WorkoutRoutes.LIBRARY) {
            LibraryScreen(onBack = back, onOpenExercise = { navController.navigate(WorkoutRoutes.exercise(it)) })
        }
        composable(WorkoutRoutes.EXERCISE) { ExerciseDetailScreen(onBack = back) }
        composable(WorkoutRoutes.PLANS) {
            PlansScreen(onBack = back, onOpenPlan = { navController.navigate(WorkoutRoutes.plan(it)) })
        }
        composable(WorkoutRoutes.PLAN) {
            PlanDetailScreen(onBack = back, onOpenSession = { navController.navigate(WorkoutRoutes.SESSION) })
        }
        composable(WorkoutRoutes.ROUTINES) {
            RoutinesScreen(
                onBack = back,
                onOpenEditor = { navController.navigate(WorkoutRoutes.routineEditor(it)) },
                onOpenSession = { navController.navigate(WorkoutRoutes.SESSION) },
            )
        }
        composable(WorkoutRoutes.ROUTINE_EDITOR) { RoutineEditorScreen(onBack = back) }
        composable(WorkoutRoutes.SESSION) {
            SessionScreen(
                onBack = back,
                onFinished = { id ->
                    navController.navigate(WorkoutRoutes.historyDetail(id)) {
                        popUpTo(WorkoutRoutes.SESSION) { inclusive = true }
                    }
                },
            )
        }
        composable(WorkoutRoutes.HISTORY) {
            HistoryScreen(onBack = back, onOpenSession = { navController.navigate(WorkoutRoutes.historyDetail(it)) })
        }
        composable(WorkoutRoutes.HISTORY_DETAIL) { HistoryDetailScreen(onBack = back) }
        composable(WorkoutRoutes.RECORDS) {
            RecordsScreen(onBack = back, onOpenExercise = { navController.navigate(WorkoutRoutes.exercise(it)) })
        }
    }
}
