package com.example.fitapp.sync.handlers;

import com.example.fitapp.nutrition.CustomFood;
import com.example.fitapp.nutrition.CustomFoodRepository;
import com.example.fitapp.nutrition.FoodMapper;
import com.example.fitapp.sync.BaseSyncHandler;
import com.example.fitapp.sync.RowRejected;
import com.example.fitapp.sync.SyncRows.CustomFoodRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class CustomFoodSyncHandler extends BaseSyncHandler {

    private final CustomFoodRepository foods;

    public CustomFoodSyncHandler(ObjectMapper mapper, Validator validator, CustomFoodRepository foods) {
        super(mapper, validator);
        this.foods = foods;
    }

    @Override
    public String entity() {
        return "custom_foods";
    }

    @Override
    public RowOutcome push(UUID userId, ObjectNode node) {
        CustomFoodRow r = parse(node, CustomFoodRow.class);
        requireOwner(r.userId(), userId);

        CustomFood existing = foods.findById(r.id()).orElse(null);
        if (existing != null && !userId.equals(existing.getUserId())) {
            throw new RowRejected("id already in use");
        }
        if (existing != null && isStale(existing.getUpdatedAt(), r.updatedAt())) {
            return stale(r.id());
        }
        if (r.deletedAt() != null && existing == null) {
            return accepted(r.id());
        }

        CustomFood food = existing != null ? existing : new CustomFood(r.id(), userId);
        food.setClientUpdatedAt(r.updatedAt());
        food.setDeletedAt(r.deletedAt());
        food.apply(
                r.name().trim(),
                r.brand() == null || r.brand().isBlank() ? null : r.brand().trim(),
                r.kcalPer100g(),
                r.proteinPer100g(),
                r.carbsPer100g(),
                r.fatPer100g(),
                FoodMapper.validUnits(r.servingUnits()));
        foods.saveAndFlush(food);
        return accepted(r.id());
    }

    @Override
    public List<PulledRow> pull(UUID userId, Instant since, int limit) {
        return foods
                .findByUserIdAndSyncedAtGreaterThanEqualOrderBySyncedAtAscIdAsc(
                        userId, since, PageRequest.of(0, limit))
                .stream()
                .map(
                        f ->
                                new PulledRow(
                                        f.getSyncedAt(),
                                        toNode(
                                                new CustomFoodRow(
                                                        f.getId(),
                                                        f.getUserId(),
                                                        f.getUpdatedAt(),
                                                        f.getDeletedAt(),
                                                        f.getName(),
                                                        f.getBrand(),
                                                        f.getKcalPer100g(),
                                                        f.getProteinPer100g(),
                                                        f.getCarbsPer100g(),
                                                        f.getFatPer100g(),
                                                        f.getServingUnits()))))
                .toList();
    }
}
