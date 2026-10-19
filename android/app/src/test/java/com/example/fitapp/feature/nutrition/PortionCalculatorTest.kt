package com.example.fitapp.feature.nutrition

import com.example.fitapp.feature.nutrition.domain.DayLog
import com.example.fitapp.feature.nutrition.domain.Food
import com.example.fitapp.feature.nutrition.domain.Macros
import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.nutrition.domain.PortionCalculator
import com.example.fitapp.feature.nutrition.domain.ServingUnit
import com.example.fitapp.fakes.food
import com.example.fitapp.fakes.mealItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PortionCalculatorTest {
    @Test
    fun `quantities accept commas and reject junk or non positive values`() {
        assertEquals(2.5, PortionCalculator.parseQuantity("2,5")!!, 0.0)
        assertEquals(150.0, PortionCalculator.parseQuantity(" 150 ")!!, 0.0)
        assertNull(PortionCalculator.parseQuantity(""))
        assertNull(PortionCalculator.parseQuantity("abc"))
        assertNull(PortionCalculator.parseQuantity("0"))
        assertNull(PortionCalculator.parseQuantity("-3"))
    }

    @Test
    fun `grams come from the unit weight times the quantity`() {
        assertEquals(90.0, PortionCalculator.gramsFor(ServingUnit("1 piece", 45.0), 2.0), 0.0)
        assertEquals(150.0, PortionCalculator.gramsFor(ServingUnit("g", 1.0), 150.0), 0.0)
        assertEquals(60.0, PortionCalculator.gramsFor(ServingUnit("1 cup", 240.0), 0.25), 0.0)
    }

    @Test
    fun `portion limits match the server`() {
        assertTrue(PortionCalculator.isValidGrams(0.1))
        assertTrue(PortionCalculator.isValidGrams(10_000.0))
        assertFalse(PortionCalculator.isValidGrams(0.05))
        assertFalse(PortionCalculator.isValidGrams(10_000.1))
    }

    @Test
    fun `macros scale with the portion`() {
        val logged = Macros(300.0, 20.0, 40.0, 6.0)
        val doubled = PortionCalculator.rescale(logged, oldGrams = 100.0, newGrams = 200.0)
        assertEquals(600.0, doubled.calories, 0.0001)
        assertEquals(40.0, doubled.proteinG, 0.0001)
        val half = PortionCalculator.rescale(logged, 100.0, 50.0)
        assertEquals(150.0, half.calories, 0.0001)
        // a zero old weight cannot be scaled, so the macros are left alone
        assertEquals(logged, PortionCalculator.rescale(logged, 0.0, 50.0))
    }

    @Test
    fun `search patterns escape wildcards and lower case`() {
        assertEquals("%chicken%", PortionCalculator.likePattern("  Chicken "))
        assertEquals("%100!%%", PortionCalculator.likePattern("100%"))
        assertEquals("%a!_b%", PortionCalculator.likePattern("a_b"))
        assertEquals("%wow!!%", PortionCalculator.likePattern("wow!"))
    }

    @Test
    fun `rounding helpers`() {
        assertEquals(1.2, PortionCalculator.round1(1.24), 0.0)
        assertEquals(1.25, PortionCalculator.round2(1.249), 0.0)
    }

    // ------------------------------------------------------------ foods and days

    @Test
    fun `food macros are per 100 g scaled to the portion`() {
        val chicken = food(kcal = 165.0, protein = 31.0, carbs = 0.0, fat = 3.6)
        val m = chicken.macrosFor(150.0)
        assertEquals(247.5, m.calories, 0.0001)
        assertEquals(46.5, m.proteinG, 0.0001)
        assertEquals(5.4, m.fatG, 0.0001)
    }

    @Test
    fun `units always start with grams and never repeat it`() {
        val rice: Food = food(units = listOf(ServingUnit("1 cup", 158.0), ServingUnit("G", 1.0)))
        assertEquals(listOf("g", "1 cup"), rice.units.map { it.label })
        assertEquals(listOf("g"), food().units.map { it.label })
    }

    @Test
    fun `a day sums its items and splits them by meal`() {
        val date = LocalDate.of(2026, 3, 2)
        val log =
            DayLog(
                date,
                listOf(
                    mealItem("a", type = MealType.BREAKFAST, kcal = 300.0),
                    mealItem("b", type = MealType.LUNCH, kcal = 600.0),
                    mealItem("c", type = MealType.LUNCH, kcal = 100.0),
                ),
                waterGlasses = 4,
            )
        assertEquals(1000.0, log.totals.calories, 0.0001)
        assertEquals(700.0, log.totalsFor(MealType.LUNCH).calories, 0.0001)
        assertEquals(listOf("b", "c"), log.itemsFor(MealType.LUNCH).map { it.id })
        assertTrue(log.itemsFor(MealType.DINNER).isEmpty())
        assertEquals(0.0, log.totalsFor(MealType.DINNER).calories, 0.0)
    }

    @Test
    fun `the default meal follows the time of day`() {
        assertEquals(MealType.SNACK, MealType.forHour(2))
        assertEquals(MealType.BREAKFAST, MealType.forHour(7))
        assertEquals(MealType.LUNCH, MealType.forHour(13))
        assertEquals(MealType.DINNER, MealType.forHour(19))
        assertEquals(MealType.SNACK, MealType.forHour(23))
    }
}
