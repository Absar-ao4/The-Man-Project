package com.example.fitapp.nutrition;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.nutrition.dto.NutritionDtos.CustomFoodDto;
import com.example.fitapp.nutrition.dto.NutritionDtos.CustomFoodRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomFoodService {

    private final CustomFoodRepository foods;

    public CustomFoodService(CustomFoodRepository foods) {
        this.foods = foods;
    }

    @Transactional(readOnly = true)
    public List<CustomFoodDto> list(UUID userId) {
        return foods.findByUserIdAndDeletedAtIsNullOrderByNameAsc(userId).stream()
                .map(FoodMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomFoodDto get(UUID userId, UUID id) {
        return FoodMapper.toDto(require(userId, id));
    }

    @Transactional
    public CustomFoodDto create(UUID userId, CustomFoodRequest req) {
        UUID id = req.id() != null ? req.id() : UUID.randomUUID();
        CustomFood existing = foods.findById(id).orElse(null);
        if (existing != null) {
            if (!userId.equals(existing.getUserId())) {
                throw new ApiException(ErrorCode.CONFLICT, "That id is already in use");
            }
            existing.setDeletedAt(null);
            return FoodMapper.toDto(foods.saveAndFlush(apply(existing, req)));
        }
        return FoodMapper.toDto(foods.saveAndFlush(apply(new CustomFood(id, userId), req)));
    }

    @Transactional
    public CustomFoodDto update(UUID userId, UUID id, CustomFoodRequest req) {
        return FoodMapper.toDto(foods.saveAndFlush(apply(require(userId, id), req)));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        CustomFood food = require(userId, id);
        food.setDeletedAt(Instant.now());
        foods.saveAndFlush(food);
    }

    private CustomFood require(UUID userId, UUID id) {
        return foods.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> ApiException.notFound("Custom food not found"));
    }

    private static CustomFood apply(CustomFood food, CustomFoodRequest r) {
        food.apply(
                r.name().trim(),
                r.brand() == null || r.brand().isBlank() ? null : r.brand().trim(),
                r.kcalPer100g(),
                r.proteinPer100g(),
                r.carbsPer100g(),
                r.fatPer100g(),
                FoodMapper.validUnits(r.servingUnits()));
        return food;
    }
}
