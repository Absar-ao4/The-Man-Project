package com.example.fitapp.feature.diet.data

import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.network.DietApi
import com.example.fitapp.core.network.dto.PriceRequestDto
import com.example.fitapp.core.network.safeApiCall
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.core.util.map
import com.example.fitapp.feature.diet.domain.CheapestProteinList
import com.example.fitapp.feature.diet.domain.DietFood
import com.example.fitapp.feature.diet.domain.DietPlan
import com.example.fitapp.feature.diet.domain.DietRepository
import com.example.fitapp.feature.diet.domain.DietType
import com.example.fitapp.feature.diet.domain.FoodList
import com.example.fitapp.feature.diet.domain.PlanRequest
import com.example.fitapp.feature.diet.domain.PlanSummary
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DietRepositoryImpl
    @Inject
    constructor(
        private val api: DietApi,
    ) : DietRepository {
        override suspend fun foods(
            dietType: DietType?,
            currency: String?,
        ): AppResult<FoodList> = safeApiCall { api.foods(dietType?.name, currency) }.map { it.toDomain() }

        override suspend fun setPrice(
            foodId: String,
            price: Double,
        ): AppResult<DietFood> {
            if (!(price > 0.0) || price > MAX_PRICE) {
                return AppResult.Failure(ApiFailure.Invalid("Enter a price above 0."))
            }
            return safeApiCall { api.setPrice(foodId, PriceRequestDto(price)) }.map { it.toDomain() }
        }

        override suspend fun resetPrice(foodId: String): AppResult<DietFood> = safeApiCall { api.clearPrice(foodId) }.map { it.toDomain() }

        override suspend fun cheapestProtein(
            dietType: DietType?,
            currency: String?,
        ): AppResult<CheapestProteinList> = safeApiCall { api.cheapestProtein(dietType?.name, currency) }.map { it.toDomain() }

        override suspend fun createPlan(request: PlanRequest): AppResult<DietPlan> =
            safeApiCall { api.createPlan(request.toDto()) }.map { it.toDomain() }

        override suspend fun plans(): AppResult<List<PlanSummary>> = safeApiCall { api.plans() }.map { list -> list.map { it.toDomain() } }

        override suspend fun plan(id: String): AppResult<DietPlan> = safeApiCall { api.plan(id) }.map { it.toDomain() }

        override suspend fun deletePlan(id: String): AppResult<Unit> = safeApiCall { api.deletePlan(id) }

        private companion object {
            const val MAX_PRICE = 1_000_000.0
        }
    }