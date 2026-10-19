package com.example.fitapp.feature.diet

import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.network.DietApi
import com.example.fitapp.core.network.dto.CheapestProteinListDto
import com.example.fitapp.core.network.dto.DietFoodDto
import com.example.fitapp.core.network.dto.DietFoodListDto
import com.example.fitapp.core.network.dto.DietPlanDto
import com.example.fitapp.core.network.dto.DietPlanRequestDto
import com.example.fitapp.core.network.dto.DietPlanSummaryDto
import com.example.fitapp.core.network.dto.MacroDto
import com.example.fitapp.core.network.dto.PlanItemDto
import com.example.fitapp.core.network.dto.PlanMealDto
import com.example.fitapp.core.network.dto.PriceRequestDto
import com.example.fitapp.core.network.dto.ShortfallDto
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.diet.data.DietRepositoryImpl
import com.example.fitapp.feature.diet.data.toDomain
import com.example.fitapp.feature.diet.data.toDto
import com.example.fitapp.feature.diet.domain.Allergen
import com.example.fitapp.feature.diet.domain.Cuisine
import com.example.fitapp.feature.diet.domain.DietType
import com.example.fitapp.feature.diet.domain.PlanRequest
import com.example.fitapp.feature.diet.domain.ShortfallReason
import com.example.fitapp.feature.diet.ui.DietFormat
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException

private class ScriptedDietApi : DietApi {
    var error: Exception? = null
    var plan =
        DietPlanDto(
            id = "p1",
            createdAt = "2026-03-02T10:00:00Z",
            budget = 200.0,
            currency = "INR",
            dietType = "VEGAN",
            mealsPerDay = 2,
            totalCost = 90.0,
            feasible = false,
            shortfall = ShortfallDto("BUDGET_TOO_LOW", 300.0, 20.0, 140.0),
            targets = MacroDto(2200.0, 110.0, 260.0, 70.0),
            totals = MacroDto(1900.0, 90.0, 230.0, 60.0),
            arrangedBy = "AI",
            notes = listOf("note"),
            meals =
                listOf(
                    PlanMealDto(1, "Dinner", "  ", 40.0, MacroDto(900.0), listOf(PlanItemDto("Rice", "white_rice", 150.0, 9.0, MacroDto(540.0, 10.0, 120.0, 1.0)))),
                    PlanMealDto(0, "Lunch", "Boil the rice.", 50.0, MacroDto(1000.0), emptyList()),
                ),
        )
    var sentPlan: DietPlanRequestDto? = null
    var sentPrice: PriceRequestDto? = null

    private fun fail() = error?.let { throw it }

    override suspend fun foods(
        dietType: String?,
        currency: String?,
    ): DietFoodListDto {
        fail()
        return DietFoodListDto(
            "INR",
            listOf("INR", "USD"),
            listOf(DietFoodDto("f1", "white_rice", "White rice", "GRAIN", listOf("VEGAN", "ALIEN_DIET"), emptyList(), 360.0, 6.7, 80.0, 0.6, "kg", 1000.0, 60.0, 60.0, false, "INR")),
        )
    }

    override suspend fun setPrice(
        foodId: String,
        request: PriceRequestDto,
    ): DietFoodDto {
        sentPrice = request
        fail()
        return DietFoodDto(foodId, "white_rice", "White rice", "GRAIN", listOf("VEGAN"), emptyList(), 360.0, 6.7, 80.0, 0.6, "kg", 1000.0, request.price, 60.0, true, "INR")
    }

    override suspend fun clearPrice(foodId: String): DietFoodDto {
        fail()
        return DietFoodDto(foodId, "white_rice", "White rice", "GRAIN", listOf("VEGAN"), emptyList(), 360.0, 6.7, 80.0, 0.6, "kg", 1000.0, 60.0, 60.0, false, "INR")
    }

    override suspend fun cheapestProtein(
        dietType: String?,
        currency: String?,
    ): CheapestProteinListDto {
        fail()
        return CheapestProteinListDto("INR", dietType, emptyList())
    }

    override suspend fun createPlan(request: DietPlanRequestDto): DietPlanDto {
        sentPlan = request
        fail()
        return plan
    }

    override suspend fun plans(): List<DietPlanSummaryDto> {
        fail()
        return listOf(DietPlanSummaryDto("p1", "2026-03-02T10:00:00Z", 200.0, "INR", "WHATEVER", 3, 90.0, true, 2100.0, 110.0))
    }

    override suspend fun plan(id: String): DietPlanDto {
        fail()
        return plan
    }

    override suspend fun deletePlan(id: String) {
        fail()
    }
}

private fun httpError(
    code: Int,
    body: String = "{}",
) = HttpException(retrofit2.Response.error<Any>(code, body.toResponseBody(null)))

class DietDataTest {
    private val api = ScriptedDietApi()
    private val repo = DietRepositoryImpl(api)

    private val request =
        PlanRequest(
            budget = 200.0,
            currency = "INR",
            dietType = DietType.VEGAN,
            allergies = setOf(Allergen.SOY, Allergen.DAIRY),
            excludedFoodIds = setOf("b", "a"),
            mealsPerDay = 3,
            cuisine = Cuisine.INDIAN,
        )

    @Test
    fun `the plan request is sent with sorted choices and plain names`() =
        runTest {
            repo.createPlan(request)

            val sent = api.sentPlan!!
            assertEquals(200.0, sent.budget, 0.0)
            assertEquals("VEGAN", sent.dietType)
            assertEquals(listOf("DAIRY", "SOY"), sent.allergies)
            assertEquals(listOf("a", "b"), sent.excludedFoodIds)
            assertEquals("INDIAN", sent.cuisine)
            assertEquals(request.toDto(), sent)
        }

    @Test
    fun `a plan is mapped with meals in order, a shortfall and blank recipes dropped`() =
        runTest {
            val plan = (repo.createPlan(request) as AppResult.Success).value

            assertEquals(listOf("Lunch", "Dinner"), plan.meals.map { it.title })
            assertEquals("Boil the rice.", plan.meals[0].recipe)
            assertNull(plan.meals[1].recipe)
            assertTrue(plan.arrangedByAi)
            assertFalse(plan.feasible)
            val shortfall = plan.shortfall!!
            assertEquals(ShortfallReason.BUDGET_TOO_LOW, shortfall.reason)
            assertEquals(140.0, shortfall.minimumBudgetNeeded!!, 0.0)
            assertEquals(110.0, plan.targets.proteinG, 0.0)
            assertEquals("Rice", plan.meals[1].items[0].foodName)
            assertEquals(540.0, plan.meals[1].items[0].macros.calories, 0.0)
        }

    @Test
    fun `an unknown reason counts as unreachable and an unknown diet falls back`() {
        val dto = ScriptedDietApi().plan.copy(shortfall = ShortfallDto("SOMETHING_NEW"), dietType = "SOMETHING_NEW", arrangedBy = "RULES", cuisine = "MARS")
        val plan = dto.toDomain()

        assertEquals(ShortfallReason.TARGETS_UNREACHABLE, plan.shortfall!!.reason)
        assertEquals(DietType.NON_VEGETARIAN, plan.dietType)
        assertEquals(Cuisine.ANY, plan.cuisine)
        assertFalse(plan.arrangedByAi)
    }

    @Test
    fun `foods keep the diets we know and drop the ones we do not`() =
        runTest {
            val list = (repo.foods(null, null) as AppResult.Success).value

            assertEquals("INR", list.currency)
            assertEquals(listOf("INR", "USD"), list.supportedCurrencies)
            assertEquals(setOf(DietType.VEGAN), list.foods.single().dietTypes)
        }

    @Test
    fun `a summary with an unknown diet still maps`() =
        runTest {
            val plans = (repo.plans() as AppResult.Success).value

            assertEquals(DietType.NON_VEGETARIAN, plans.single().dietType)
        }

    @Test
    fun `a price must be above zero and is not sent otherwise`() =
        runTest {
            assertTrue(repo.setPrice("f1", 0.0) is AppResult.Failure)
            assertTrue(repo.setPrice("f1", -2.0) is AppResult.Failure)
            assertTrue(repo.setPrice("f1", Double.NaN) is AppResult.Failure)
            assertTrue(repo.setPrice("f1", 2_000_000.0) is AppResult.Failure)
            assertNull(api.sentPrice)

            val ok = repo.setPrice("f1", 42.5) as AppResult.Success
            assertEquals(42.5, ok.value.price, 0.0)
            assertTrue(ok.value.overridden)
        }

    @Test
    fun `errors keep the server message when there is one`() =
        runTest {
            api.error = httpError(422, """{"detail":"No foods match your diet type."}""")
            val refused = (repo.createPlan(request) as AppResult.Failure).failure
            assertEquals(ApiFailure.Invalid("No foods match your diet type."), refused)

            api.error = httpError(400, """{"detail":"Check the highlighted fields.","errors":{"targets":"Set your daily targets first."}}""")
            val invalid = (repo.createPlan(request) as AppResult.Failure).failure as ApiFailure.Invalid
            assertEquals("Set your daily targets first.", invalid.fieldErrors["targets"])

            api.error = IOException("offline")
            assertEquals(ApiFailure.Offline, (repo.foods(null, null) as AppResult.Failure).failure)

            api.error = httpError(404)
            assertEquals(ApiFailure.NotFound, (repo.plan("x") as AppResult.Failure).failure)
        }

    @Test
    fun `number formatting is the same on every device`() {
        assertEquals("200 INR", DietFormat.money(200.0, "INR"))
        assertEquals("2.4 USD", DietFormat.money(2.4, "USD"))
        assertEquals("1,250.5 INR", DietFormat.money(1250.5, "INR"))
        assertEquals("0.33 USD", DietFormat.money(0.333, "USD"))
        assertEquals("120 g", DietFormat.grams(120.0))
        assertEquals("120 g", DietFormat.grams(119.6))
        assertEquals("7.5 g", DietFormat.grams(7.5))
        assertEquals("2026-03-02", DietFormat.day("2026-03-02T10:00:00Z"))
    }
}