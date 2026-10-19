package com.example.fitapp.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.fitapp.R
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.feature.account.ui.DataScreen
import com.example.fitapp.feature.articles.ui.ArticleScreen
import com.example.fitapp.feature.articles.ui.ArticlesScreen
import com.example.fitapp.feature.body.ui.BodyScreen
import com.example.fitapp.feature.dashboard.ui.HomeNavigation
import com.example.fitapp.feature.dashboard.ui.HomeScreen
import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.progress.ui.ProgressScreen
import com.example.fitapp.feature.reminders.ui.RemindersScreen
import com.example.fitapp.feature.gamification.ui.CelebrationHost
import com.example.fitapp.feature.social.ui.AddFriendScreen
import com.example.fitapp.feature.social.ui.PrivacyScreen
import com.example.fitapp.feature.social.ui.SocialScreen
import com.example.fitapp.feature.profile.ui.ProfileScreen

enum class MainTab(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    HOME("home", R.string.tab_home, Icons.Filled.Home),
    WORKOUT(WorkoutRoutes.GRAPH, R.string.tab_workout, Icons.Filled.FitnessCenter),
    FOOD(NutritionRoutes.GRAPH, R.string.tab_food, Icons.Filled.Restaurant),
    SOCIAL("social", R.string.tab_social, Icons.Filled.Groups),
    PROFILE("profile", R.string.tab_profile, Icons.Filled.Person),
}

object ProgressRoutes {
    const val PROGRESS = "progress"
    const val BODY = "body"
}

object ProfileRoutes {
    const val REMINDERS = "profile/reminders"
    const val DATA = "profile/data"
}

object ArticleRoutes {
    const val LIST = "articles"
    const val ARTICLE = "articles/{slug}"

    fun article(slug: String) = "articles/$slug"
}

object SocialRoutes {
    const val ADD_FRIEND = "social/add"
    const val PRIVACY = "social/privacy"
}

/** Screens that show the bottom bar. Pushed screens (session, detail pages) cover it. */
private val TopLevelRoutes = setOf("home", WorkoutRoutes.HUB, NutritionRoutes.LOG, "social", "profile")

/** Five-tab shell. The selected tab gets an accent pill behind its icon. */
@Composable
fun MainScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val colors = ManTheme.colors
    val showBottomBar = destination?.route in TopLevelRoutes

    val openTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // A tapped notification names the screen it wants: a tab, or a screen of its own such as the Body screen.
    val routerViewModel: NotificationRouterViewModel = hiltViewModel()
    val requestedRoute by routerViewModel.router.pending.collectAsState()
    LaunchedEffect(requestedRoute) {
        requestedRoute?.let { route ->
            if (isTabRoute(route)) openTab(route) else runCatching { navController.navigate(route) }
            routerViewModel.router.consume()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    MainTab.entries.forEach { tab ->
                        val selected = destination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { openTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                            colors =
                                NavigationBarItemDefaults.colors(
                                    indicatorColor = colors.accent,
                                    selectedIconColor = colors.onAccent,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unselectedIconColor = colors.muted,
                                    unselectedTextColor = colors.muted,
                                ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = MainTab.HOME.route,
            // Nested screens bring their own top bars; telling them the insets are already handled
            // stops the status bar padding from being applied twice.
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(MainTab.HOME.route) {
                HomeScreen(
                    navigation =
                        HomeNavigation(
                            onLogFood = { openTab(MainTab.FOOD.route) },
                            onScanMeal = {
                                openTab(MainTab.FOOD.route)
                                navController.navigate(NutritionRoutes.scan(java.time.LocalDate.now(), MealType.forHour(java.time.LocalTime.now().hour)))
                            },
                            onStartWorkout = { openTab(MainTab.WORKOUT.route) },
                            onLockIn = {
                                openTab(MainTab.WORKOUT.route)
                                navController.navigate(WorkoutRoutes.LOCKIN_PROGRAMS)
                            },
                            onProgress = { navController.navigate(ProgressRoutes.PROGRESS) },
                            onBody = { navController.navigate(ProgressRoutes.BODY) },
                            onLeague = { openTab(MainTab.SOCIAL.route) },
                        ),
                )
            }
            composable(ProgressRoutes.PROGRESS) {
                ProgressScreen(onBack = { navController.popBackStack() }, onOpenBody = { navController.navigate(ProgressRoutes.BODY) })
            }
            composable(ProgressRoutes.BODY) { BodyScreen(onBack = { navController.popBackStack() }) }
            workoutGraph(navController)
            nutritionGraph(navController)
            composable(MainTab.SOCIAL.route) {
                SocialScreen(
                    onAddFriend = { navController.navigate(SocialRoutes.ADD_FRIEND) },
                    onPrivacy = { navController.navigate(SocialRoutes.PRIVACY) },
                )
            }
            composable(SocialRoutes.ADD_FRIEND) { AddFriendScreen(onBack = { navController.popBackStack() }) }
            composable(SocialRoutes.PRIVACY) { PrivacyScreen(onBack = { navController.popBackStack() }) }
            composable(MainTab.PROFILE.route) {
                ProfileScreen(
                    onOpenReminders = { navController.navigate(ProfileRoutes.REMINDERS) },
                    onOpenProgress = { navController.navigate(ProgressRoutes.PROGRESS) },
                    onOpenArticles = { navController.navigate(ArticleRoutes.LIST) },
                    onOpenData = { navController.navigate(ProfileRoutes.DATA) },
                )
            }
            composable(ProfileRoutes.REMINDERS) { RemindersScreen(onBack = { navController.popBackStack() }) }
            composable(ProfileRoutes.DATA) { DataScreen(onBack = { navController.popBackStack() }) }
            composable(ArticleRoutes.LIST) {
                ArticlesScreen(onBack = { navController.popBackStack() }, onOpenArticle = { navController.navigate(ArticleRoutes.article(it)) })
            }
            composable(ArticleRoutes.ARTICLE, arguments = listOf(navArgument("slug") { type = NavType.StringType })) {
                ArticleScreen(onBack = { navController.popBackStack() })
            }
        }
    }

    // XP, level-ups and badges reported by the server show up on top of whichever screen is open.
    CelebrationHost()
}
