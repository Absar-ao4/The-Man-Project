package com.example.fitapp.nutrition;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.nutrition.dto.NutritionDtos.CustomMealDto;
import com.example.fitapp.nutrition.dto.NutritionDtos.CustomMealItemDto;
import com.example.fitapp.nutrition.dto.NutritionDtos.CustomMealItemRequest;
import com.example.fitapp.nutrition.dto.NutritionDtos.CustomMealRequest;
import com.example.fitapp.sync.SyncResult;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Saved meals ("my usual breakfast"): a named list of foods with fixed portions. */
@Service
public class CustomMealService {

    private final CustomMealRepository meals;
    private final CustomMealItemRepository mealItems;

    public CustomMealService(CustomMealRepository meals, CustomMealItemRepository mealItems) {
        this.meals = meals;
        this.mealItems = mealItems;
    }

    @Transactional(readOnly = true)
    public List<CustomMealDto> list(UUID userId) {
        List<CustomMeal> all = meals.findByUserIdAndDeletedAtIsNullOrderByNameAsc(userId);
        if (all.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<CustomMealItem>> children =
                mealItems
                        .findByMealIdInAndDeletedAtIsNullOrderByOrderIndexAsc(
                                all.stream().map(CustomMeal::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(CustomMealItem::getMealId));
        return all.stream().map(m -> toDto(m, children.getOrDefault(m.getId(), List.of()))).toList();
    }

    @Transactional(readOnly = true)
    public CustomMealDto get(UUID userId, UUID id) {
        return toDto(require(userId, id));
    }

    @Transactional
    public CustomMealDto create(UUID userId, CustomMealRequest req) {
        UUID id = req.id() != null ? req.id() : UUID.randomUUID();
        CustomMeal existing = meals.findById(id).orElse(null);
        if (existing != null) {
            if (!userId.equals(existing.getUserId())) {
                throw new ApiException(ErrorCode.CONFLICT, "That id is already in use");
            }
            existing.setDeletedAt(null);
            return save(userId, existing, req, null);
        }
        return save(userId, new CustomMeal(id, userId), req, null);
    }

    @Transactional
    public CustomMealDto update(UUID userId, UUID id, CustomMealRequest req) {
        return save(userId, require(userId, id), req, null);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        CustomMeal meal = require(userId, id);
        Instant now = Instant.now();
        meal.setDeletedAt(now);
        meals.save(meal);
        for (CustomMealItem item :
                mealItems.findByMealIdAndDeletedAtIsNullOrderByOrderIndexAsc(id)) {
            item.setDeletedAt(now);
            mealItems.save(item);
        }
    }

    private CustomMealDto save(
            UUID userId, CustomMeal meal, CustomMealRequest req, Instant clientUpdatedAt) {
        if (clientUpdatedAt != null) {
            meal.setClientUpdatedAt(clientUpdatedAt);
        }
        meal.setName(req.name().trim());
        meal.setFavorite(req.favorite());
        meals.saveAndFlush(meal);

        Map<UUID, CustomMealItem> current =
                mealItems.findByMealIdAndDeletedAtIsNullOrderByOrderIndexAsc(meal.getId()).stream()
                        .collect(Collectors.toMap(CustomMealItem::getId, Function.identity()));
        Set<UUID> keep = new HashSet<>();
        int order = 0;
        for (CustomMealItemRequest r : req.items()) {
            UUID itemId = r.id() != null ? r.id() : UUID.randomUUID();
            CustomMealItem item = current.get(itemId);
            if (item == null) {
                item = reviveOrCreate(userId, meal.getId(), itemId);
            }
            item.apply(
                    order++,
                    r.foodName().trim(),
                    r.foodRef(),
                    r.grams(),
                    r.calories(),
                    r.proteinG(),
                    r.carbsG(),
                    r.fatG());
            mealItems.save(item);
            keep.add(itemId);
        }
        Instant now = Instant.now();
        for (Map.Entry<UUID, CustomMealItem> row : current.entrySet()) {
            if (!keep.contains(row.getKey())) {
                row.getValue().setDeletedAt(now);
                mealItems.save(row.getValue());
            }
        }
        mealItems.flush();
        meals.touchSynced(List.of(meal.getId()), Instant.now());
        return toDto(meal);
    }

    /** Applies a saved meal pushed by a device. Last write wins on the device edit time. */
    @Transactional
    public SyncResult syncUpsert(
            UUID userId, UUID id, CustomMealRequest req, Instant clientUpdatedAt, Instant deletedAt) {
        CustomMeal existing = meals.findById(id).orElse(null);
        if (existing != null && !userId.equals(existing.getUserId())) {
            throw new ApiException(ErrorCode.CONFLICT, "That id is already in use");
        }
        if (existing != null && existing.getUpdatedAt().isAfter(clientUpdatedAt)) {
            return SyncResult.STALE;
        }
        if (deletedAt != null) {
            if (existing != null) {
                existing.setClientUpdatedAt(clientUpdatedAt);
                existing.setDeletedAt(deletedAt);
                meals.save(existing);
                for (CustomMealItem item :
                        mealItems.findByMealIdAndDeletedAtIsNullOrderByOrderIndexAsc(id)) {
                    item.setDeletedAt(deletedAt);
                    mealItems.save(item);
                }
                meals.touchSynced(List.of(id), Instant.now());
            }
            return SyncResult.ACCEPTED;
        }
        CustomMeal meal = existing != null ? existing : new CustomMeal(id, userId);
        meal.setDeletedAt(null);
        save(userId, meal, req, clientUpdatedAt);
        return SyncResult.ACCEPTED;
    }

    /** Saved meals with their live items, in pull order, for sync. */
    @Transactional(readOnly = true)
    public List<CustomMealDto> loadForSync(List<CustomMeal> pulled) {
        if (pulled.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<CustomMealItem>> children =
                mealItems
                        .findByMealIdInAndDeletedAtIsNullOrderByOrderIndexAsc(
                                pulled.stream().map(CustomMeal::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(CustomMealItem::getMealId));
        return pulled.stream().map(m -> toDto(m, children.getOrDefault(m.getId(), List.of()))).toList();
    }

    private CustomMealItem reviveOrCreate(UUID userId, UUID mealId, UUID itemId) {
        CustomMealItem any = mealItems.findById(itemId).orElse(null);
        if (any == null) {
            return new CustomMealItem(itemId, userId, mealId);
        }
        if (!userId.equals(any.getUserId()) || !mealId.equals(any.getMealId())) {
            throw new ApiException(ErrorCode.CONFLICT, "Item id is already in use");
        }
        any.setDeletedAt(null);
        return any;
    }

    private CustomMeal require(UUID userId, UUID id) {
        return meals.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> ApiException.notFound("Meal not found"));
    }

    private CustomMealDto toDto(CustomMeal meal) {
        return toDto(
                meal, mealItems.findByMealIdAndDeletedAtIsNullOrderByOrderIndexAsc(meal.getId()));
    }

    private static CustomMealDto toDto(CustomMeal meal, List<CustomMealItem> children) {
        List<CustomMealItemDto> itemDtos =
                children.stream()
                        .map(
                                c ->
                                        new CustomMealItemDto(
                                                c.getId(),
                                                c.getFoodName(),
                                                c.getFoodRef(),
                                                c.getGrams(),
                                                c.getCalories(),
                                                c.getProteinG(),
                                                c.getCarbsG(),
                                                c.getFatG()))
                        .toList();
        return new CustomMealDto(
                meal.getId(),
                meal.getName(),
                meal.isFavorite(),
                itemDtos,
                round2(children.stream().mapToDouble(CustomMealItem::getCalories).sum()),
                round2(children.stream().mapToDouble(CustomMealItem::getProteinG).sum()),
                round2(children.stream().mapToDouble(CustomMealItem::getCarbsG).sum()),
                round2(children.stream().mapToDouble(CustomMealItem::getFatG).sum()),
                meal.getUpdatedAt());
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
