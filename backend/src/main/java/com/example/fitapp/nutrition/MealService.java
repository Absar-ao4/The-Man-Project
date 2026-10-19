package com.example.fitapp.nutrition;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.nutrition.dto.NutritionDtos.MealItemDto;
import com.example.fitapp.nutrition.dto.NutritionDtos.MealItemRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The meal log. Everything is scoped to the caller; another user's item is a plain 404. */
@Service
public class MealService {

    static final int MAX_RANGE_DAYS = 62;

    private final MealItemRepository items;

    public MealService(MealItemRepository items) {
        this.items = items;
    }

    @Transactional(readOnly = true)
    public List<MealItemDto> list(UUID userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw ApiException.validation("to", "must not be before from");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw ApiException.validation("to", "range is limited to " + MAX_RANGE_DAYS + " days");
        }
        return items
                .findByUserIdAndDateBetweenAndDeletedAtIsNullOrderByDateAscCreatedAtAsc(userId, from, to)
                .stream()
                .map(MealService::toDto)
                .toList();
    }

    @Transactional
    public MealItemDto create(UUID userId, MealItemRequest req) {
        UUID id = req.id() != null ? req.id() : UUID.randomUUID();
        MealItem existing = items.findById(id).orElse(null);
        if (existing != null) {
            if (!userId.equals(existing.getUserId())) {
                throw new ApiException(ErrorCode.CONFLICT, "That id is already in use");
            }
            existing.setDeletedAt(null);
            return toDto(items.saveAndFlush(apply(existing, req)));
        }
        return toDto(items.saveAndFlush(apply(new MealItem(id, userId), req)));
    }

    @Transactional
    public MealItemDto update(UUID userId, UUID id, MealItemRequest req) {
        return toDto(items.saveAndFlush(apply(require(userId, id), req)));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        MealItem item = require(userId, id);
        item.setDeletedAt(Instant.now());
        items.saveAndFlush(item);
    }

    private MealItem require(UUID userId, UUID id) {
        return items.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> ApiException.notFound("Meal item not found"));
    }

    private static MealItem apply(MealItem item, MealItemRequest r) {
        item.apply(
                r.date(),
                r.mealType(),
                r.foodName().trim(),
                r.foodRef(),
                r.grams(),
                r.calories(),
                r.proteinG(),
                r.carbsG(),
                r.fatG(),
                r.source() == null ? MealSource.MANUAL : r.source());
        return item;
    }

    static MealItemDto toDto(MealItem m) {
        return new MealItemDto(
                m.getId(),
                m.getDate(),
                m.getMealType(),
                m.getFoodName(),
                m.getFoodRef(),
                m.getGrams(),
                m.getCalories(),
                m.getProteinG(),
                m.getCarbsG(),
                m.getFatG(),
                m.getSource(),
                m.getUpdatedAt());
    }
}
