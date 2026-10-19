package com.example.fitapp.navigation

import android.net.Uri
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.example.fitapp.feature.diet.ui.CheapestProteinScreen
import com.example.fitapp.feature.diet.ui.DietPlanScreen
import com.example.fitapp.feature.diet.ui.DietPlannerScreen
import com.example.fitapp.feature.diet.ui.DietPricesScreen
import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.nutrition.ui.CustomFoodScreen
import com.example.fitapp.feature.nutrition.ui.FoodLogScreen
import com.example.fitapp.feature.nutrition.ui.FoodSearchScreen
import com.example.fitapp.feature.nutrition.ui.NutritionHistoryScreen
import com.example.fitapp.feature.nutrition.ui.PortionScreen
import com.example.fitapp.feature.nutrition.ui.SavedMealsScreen
import com.example.fitapp.feature.mealideas.ui.MealIdeasScreen
import com.example.fitapp.feature.photoscan.ui.PhotoScanScreen
import java.time.LocalDate

object NutritionRoutes {
    /** The nested graph; the Food tab navigates here. */
    const val GRAPH = "nutrition"
    const val LOG = "nutrition/log"
    const val SEARCH = "nutrition/search?date={date}&meal={meal}"
    const val PORTION = "nutrition/portion/{foodRef}?date={date}&meal={meal}&source={source}"
    const val CUSTOM_FOOD = "nutrition/custom-food"
    const val MEALS = "nutrition/meals?date={date}&meal={meal}"
    const val HISTORY = "nutrition/history"
    const val SCAN = "nutrition/scan?date={date}&meal={meal}"
    const val DIET = "nutrition/diet"
    const val DIET_PLAN = "nutrition/diet/plan/{planId}"
    const val DIET_PROTEIN = "nutrition/diet/protein"
    const val DIET_PRICES = "nutrition/diet/prices"
    const val IDEAS = "nutrition/ideas"

    fun dietPlan(planId: String) = "nutrition/diet/plan/$planId"

    fun scan(
        date: LocalDate,
        meal: MealType,
    ) = "nutrition/scan?date=$date&meal=${meal.name}"

    fun search(
        date: LocalDate,
        meal: MealType,
    ) = "nutrition/search?date=$date&meal=${meal.name}"

    fun portion(
        foodRef: String,
        date: String,
        meal: String,
        fromBarcode: Boolean,
    ) = "nutrition/portion/${Uri.encode(foodRef)}?date=$date&meal=$meal&source=${if (fromBarcode) "BARCODE" else "SEARCH"}"

    fun meals(
        date: LocalDate,
        meal: MealType,
    ) = "nutrition/meals?date=$date&meal=${meal.name}"
}

private fun optionalString(name: String) =
    navArgument(name) {
        type = NavType.StringType
        nullable = true
        defaultValue = null
    }

fun NavGraphBuilder.nutritionGraph(navController: NavHostController) {
    val back: () -> Unit = { navController.popBackStack() }
    val backToLog: () -> Unit = { navController.popBackStack(NutritionRoutes.LOG, inclusive = false) }

    navigation(startDestination = NutritionRoutes.LOG, route = NutritionRoutes.GRAPH) {
        composable(NutritionRoutes.LOG) {
            FoodLogScreen(
                onAddFood = { date, meal -> navController.navigate(NutritionRoutes.search(date, meal)) },
                onOpenSavedMeals = { date, meal -> navController.navigate(NutritionRoutes.meals(date, meal)) },
                onOpenHistory = { navController.navigate(NutritionRoutes.HISTORY) },
                onScanMeal = { date, meal -> navController.navigate(NutritionRoutes.scan(date, meal)) },
                onOpenDietPlanner = { navController.navigate(NutritionRoutes.DIET) },
                onOpenMealIdeas = { navController.navigate(NutritionRoutes.IDEAS) },
            )
        }
        composable(NutritionRoutes.IDEAS) { MealIdeasScreen(onBack = back) }
        composable(NutritionRoutes.SCAN, arguments = listOf(optionalString("date"), optionalString("meal"))) {
            PhotoScanScreen(onBack = back, onDone = backToLog)
        }
        composable(NutritionRoutes.SEARCH, arguments = listOf(optionalString("date"), optionalString("meal"))) { entry ->
            val date = entry.arguments?.getString("date").orEmpty()
            val meal = entry.arguments?.getString("meal").orEmpty()
            FoodSearchScreen(
                onBack = back,
                onOpenPortion = { foodRef, fromBarcode ->
                    navController.navigate(NutritionRoutes.portion(foodRef, date, meal, fromBarcode))
                },
                onCreateFood = { navController.navigate(NutritionRoutes.CUSTOM_FOOD) },
            )
        }
        composable(
            NutritionRoutes.PORTION,
            arguments =
                listOf(
                    navArgument("foodRef") { type = NavType.StringType },
                    optionalString("date"),
                    optionalString("meal"),
                    optionalString("source"),
                ),
        ) {
            PortionScreen(onBack = back, onAdded = backToLog)
        }
        composable(NutritionRoutes.CUSTOM_FOOD) { CustomFoodScreen(onBack = back) }
        composable(NutritionRoutes.MEALS, arguments = listOf(optionalString("date"), optionalString("meal"))) {
            SavedMealsScreen(onBack = back, onLogged = backToLog)
        }
        composable(NutritionRoutes.HISTORY) { NutritionHistoryScreen(onBack = back) }
        composable(NutritionRoutes.DIET) {
            DietPlannerScreen(
                onBack = back,
                onOpenPlan = { planId -> navController.navigate(NutritionRoutes.dietPlan(planId)) },
                onCheapestProtein = { navController.navigate(NutritionRoutes.DIET_PROTEIN) },
                onPrices = { navController.navigate(NutritionRoutes.DIET_PRICES) },
            )
        }
        composable(NutritionRoutes.DIET_PLAN, arguments = listOf(navArgument("planId") { type = NavType.StringType })) {
            DietPlanScreen(onBack = back)
        }
        composable(NutritionRoutes.DIET_PROTEIN) { CheapestProteinScreen(onBack = back) }
        composable(NutritionRoutes.DIET_PRICES) { DietPricesScreen(onBack = back) }
    }
}
