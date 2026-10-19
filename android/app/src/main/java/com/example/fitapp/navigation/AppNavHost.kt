package com.example.fitapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.fitapp.core.designsystem.components.ErrorState
import com.example.fitapp.core.designsystem.components.LoadingState
import com.example.fitapp.feature.auth.ui.LoginScreen
import com.example.fitapp.feature.auth.ui.RegisterScreen
import com.example.fitapp.feature.profile.ui.OnboardingScreen

@Composable
fun AppNavHost(rootViewModel: RootViewModel = hiltViewModel()) {
    val state by rootViewModel.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    // The session and profile decide where the user belongs; screens never navigate on login success.
    LaunchedEffect(state) {
        val current = navController.currentDestination?.route
        val target =
            when (state) {
                RootState.LoggedOut -> if (current == Routes.REGISTER) Routes.REGISTER else Routes.LOGIN
                RootState.NeedsOnboarding -> Routes.ONBOARDING
                RootState.Ready -> Routes.MAIN
                RootState.Loading, is RootState.Error -> Routes.SPLASH
            }
        if (current != target) {
            navController.navigate(target) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            when (val s = state) {
                is RootState.Error -> ErrorState(message = s.message, onRetry = rootViewModel::retry)
                else -> LoadingState()
            }
        }
        composable(Routes.LOGIN) {
            LoginScreen(onNavigateToRegister = { navController.navigate(Routes.REGISTER) })
        }
        composable(Routes.REGISTER) {
            RegisterScreen(onNavigateToLogin = { navController.popBackStack() })
        }
        composable(Routes.ONBOARDING) { OnboardingScreen() }
        composable(Routes.MAIN) { MainScaffold() }
    }
}
