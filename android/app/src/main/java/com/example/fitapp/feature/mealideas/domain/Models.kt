package com.example.fitapp.feature.mealideas.domain

import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.diet.domain.Allergen
import com.example.fitapp.feature.diet.domain.DietType
import com.example.fitapp.feature.nutrition.domain.Macros
import com.example.fitapp.feature.nutrition.domain.MealType
import java.time.LocalDate

enum class IdeaSource { AI, RULES }

data class IdeaItem(
    val foodSlug: String,
    val name: String,
    val grams: Double,
    val macros: Macros,
)

/** One meal idea. The numbers are worked out by the server from its food list, never taken from the AI. */
data class MealIdea(
    val title: String,
    val description: String,
    val totals: Macros,
    val items: List<IdeaItem>,
)

/** What was asked for, and the ideas that fit what is left of today. */
data class MealIdeas(
    val date: LocalDate,
    val targets: Macros,
    val eaten: Macros,
    val remaining: Macros,
    val source: IdeaSource,
    val notes: List<String>,
    val ideas: List<MealIdea>,
    val usesLeftToday: Int,
)

data class IdeaRequest(
    val mealType: MealType?,
    val dietType: DietType,
    val allergies: Set<Allergen>,
)

interface MealIdeaRepository {
    /** Needs the network and daily targets. */
    suspend fun ideas(request: IdeaRequest): AppResult<MealIdeas>
}

/** The words around the ideas, kept out of the composables so they can be tested. */
object IdeaText {
    /** "850 kcal and 62 g protein left today". */
    fun remaining(left: Macros): String = "${Math.round(left.calories)} kcal and ${Math.round(left.proteinG)} g protein left today"

    /** "650 kcal, 45 g protein, 70 g carbs, 15 g fat". */
    fun macros(m: Macros): String =
        "${Math.round(m.calories)} kcal, ${Math.round(m.proteinG)} g protein, ${Math.round(m.carbsG)} g carbs, ${Math.round(m.fatG)} g fat"

    /** "Chicken breast, raw: 120 g" shortened to the food and the weight. */
    fun item(item: IdeaItem): String = "${item.name}: ${gramsText(item.grams)}"

    fun gramsText(grams: Double): String = if (grams % 1.0 == 0.0) "${grams.toInt()} g" else "${Math.round(grams * 10) / 10.0} g"

    fun sourceLabel(source: IdeaSource): String =
        when (source) {
            IdeaSource.AI -> "Ideas from the AI, checked against our food list"
            IdeaSource.RULES -> "Ideas from simple rules"
        }

    fun usesLeft(source: IdeaSource, left: Int): String? =
        when {
            source == IdeaSource.AI && left > 0 -> "$left AI ${if (left == 1) "try" else "tries"} left today"
            source == IdeaSource.AI -> "That was your last AI try today"
            else -> null
        }
}