package com.example.fitapp.sync.handlers;

import com.example.fitapp.nutrition.CustomMeal;
import com.example.fitapp.nutrition.CustomMealRepository;
import com.example.fitapp.nutrition.CustomMealService;
import com.example.fitapp.nutrition.dto.NutritionDtos.CustomMealDto;
import com.example.fitapp.nutrition.dto.NutritionDtos.CustomMealItemRequest;
import com.example.fitapp.nutrition.dto.NutritionDtos.CustomMealRequest;
import com.example.fitapp.sync.BaseSyncHandler;
import com.example.fitapp.sync.RowRejected;
import com.example.fitapp.sync.SyncResult;
import com.example.fitapp.sync.SyncRows.CustomMealItemRow;
import com.example.fitapp.sync.SyncRows.CustomMealRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** Saved meals travel with their items as one unit. */
@Component
public class CustomMealSyncHandler extends BaseSyncHandler {

    private final CustomMealRepository meals;
    private final CustomMealService service;

    public CustomMealSyncHandler(
            ObjectMapper mapper, Validator validator, CustomMealRepository meals, CustomMealService service) {
        super(mapper, validator);
        this.meals = meals;
        this.service = service;
    }

    @Override
    public String entity() {
        return "custom_meals";
    }

    @Override
    public RowOutcome push(UUID userId, ObjectNode node) {
        CustomMealRow r = parse(node, CustomMealRow.class);
        requireOwner(r.userId(), userId);
        List<CustomMealItemRow> items = orEmpty(r.items());
        if (r.deletedAt() == null && items.isEmpty()) {
            throw new RowRejected("a saved meal needs at least one item");
        }

        List<CustomMealItemRequest> children = new ArrayList<>();
        for (CustomMealItemRow i : items) {
            children.add(
                    new CustomMealItemRequest(
                            i.id(), i.foodName(), i.foodRef(), i.grams(), i.calories(), i.proteinG(), i.carbsG(), i.fatG()));
        }
        CustomMealRequest req = new CustomMealRequest(r.id(), r.name(), r.favorite(), children);
        SyncResult result = service.syncUpsert(userId, r.id(), req, r.updatedAt(), r.deletedAt());
        return result == SyncResult.STALE ? stale(r.id()) : accepted(r.id());
    }

    @Override
    public List<PulledRow> pull(UUID userId, Instant since, int limit) {
        List<CustomMeal> pulled =
                meals.findByUserIdAndSyncedAtGreaterThanEqualOrderBySyncedAtAscIdAsc(
                        userId, since, PageRequest.of(0, limit));
        List<CustomMealDto> dtos = service.loadForSync(pulled);
        List<PulledRow> rows = new ArrayList<>();
        for (int i = 0; i < pulled.size(); i++) {
            CustomMeal m = pulled.get(i);
            List<CustomMealItemRow> items =
                    dtos.get(i).items().stream()
                            .map(
                                    x ->
                                            new CustomMealItemRow(
                                                    x.id(),
                                                    x.foodName(),
                                                    x.foodRef(),
                                                    x.grams(),
                                                    x.calories(),
                                                    x.proteinG(),
                                                    x.carbsG(),
                                                    x.fatG()))
                            .toList();
            rows.add(
                    new PulledRow(
                            m.getSyncedAt(),
                            toNode(
                                    new CustomMealRow(
                                            m.getId(),
                                            m.getUserId(),
                                            m.getUpdatedAt(),
                                            m.getDeletedAt(),
                                            m.getName(),
                                            m.isFavorite(),
                                            items))));
        }
        return rows;
    }
}
