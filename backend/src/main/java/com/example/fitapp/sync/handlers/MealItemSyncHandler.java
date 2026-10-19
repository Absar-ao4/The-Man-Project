package com.example.fitapp.sync.handlers;

import com.example.fitapp.nutrition.MealItem;
import com.example.fitapp.nutrition.MealItemRepository;
import com.example.fitapp.nutrition.MealSource;
import com.example.fitapp.sync.BaseSyncHandler;
import com.example.fitapp.sync.RowRejected;
import com.example.fitapp.sync.SyncRows.MealItemRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class MealItemSyncHandler extends BaseSyncHandler {

    private final MealItemRepository items;

    public MealItemSyncHandler(ObjectMapper mapper, Validator validator, MealItemRepository items) {
        super(mapper, validator);
        this.items = items;
    }

    @Override
    public String entity() {
        return "meal_items";
    }

    @Override
    public RowOutcome push(UUID userId, ObjectNode node) {
        MealItemRow r = parse(node, MealItemRow.class);
        requireOwner(r.userId(), userId);

        MealItem existing = items.findById(r.id()).orElse(null);
        if (existing != null && !userId.equals(existing.getUserId())) {
            throw new RowRejected("id already in use");
        }
        if (existing != null && isStale(existing.getUpdatedAt(), r.updatedAt())) {
            return stale(r.id());
        }
        if (r.deletedAt() != null && existing == null) {
            return accepted(r.id());
        }

        MealItem item = existing != null ? existing : new MealItem(r.id(), userId);
        item.setClientUpdatedAt(r.updatedAt());
        item.setDeletedAt(r.deletedAt());
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
        items.saveAndFlush(item);
        return accepted(r.id());
    }

    @Override
    public List<PulledRow> pull(UUID userId, Instant since, int limit) {
        return items
                .findByUserIdAndSyncedAtGreaterThanEqualOrderBySyncedAtAscIdAsc(
                        userId, since, PageRequest.of(0, limit))
                .stream()
                .map(
                        m ->
                                new PulledRow(
                                        m.getSyncedAt(),
                                        toNode(
                                                new MealItemRow(
                                                        m.getId(),
                                                        m.getUserId(),
                                                        m.getUpdatedAt(),
                                                        m.getDeletedAt(),
                                                        m.getDate(),
                                                        m.getMealType(),
                                                        m.getFoodName(),
                                                        m.getFoodRef(),
                                                        m.getGrams(),
                                                        m.getCalories(),
                                                        m.getProteinG(),
                                                        m.getCarbsG(),
                                                        m.getFatG(),
                                                        m.getSource()))))
                .toList();
    }
}
