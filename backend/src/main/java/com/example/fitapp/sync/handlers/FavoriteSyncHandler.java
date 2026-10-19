package com.example.fitapp.sync.handlers;

import com.example.fitapp.nutrition.FavoriteFood;
import com.example.fitapp.nutrition.FavoriteFoodRepository;
import com.example.fitapp.nutrition.FoodMapper;
import com.example.fitapp.sync.BaseSyncHandler;
import com.example.fitapp.sync.RowRejected;
import com.example.fitapp.sync.SyncRows.FavoriteRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Favorites are unique per user and food. Two devices favoriting the same food with different ids
 * end up on one row, and the second device is told to adopt the first one's id.
 */
@Component
public class FavoriteSyncHandler extends BaseSyncHandler {

    private final FavoriteFoodRepository favorites;

    public FavoriteSyncHandler(ObjectMapper mapper, Validator validator, FavoriteFoodRepository favorites) {
        super(mapper, validator);
        this.favorites = favorites;
    }

    @Override
    public String entity() {
        return "favorite_foods";
    }

    @Override
    public RowOutcome push(UUID userId, ObjectNode node) {
        FavoriteRow r = parse(node, FavoriteRow.class);
        requireOwner(r.userId(), userId);

        FavoriteFood byId = favorites.findById(r.id()).orElse(null);
        if (byId != null && !userId.equals(byId.getUserId())) {
            throw new RowRejected("id already in use");
        }
        if (byId != null && !byId.getFoodRef().equals(r.foodRef())) {
            throw new RowRejected("foodRef cannot be changed");
        }

        FavoriteFood sameFood = favorites.findByUserIdAndFoodRef(userId, r.foodRef()).orElse(null);
        FavoriteFood target = sameFood != null ? sameFood : byId;
        if (target == null) {
            if (r.deletedAt() != null) {
                return accepted(r.id());
            }
            target = new FavoriteFood(r.id(), userId, r.foodRef());
        }
        boolean remapped = !target.getId().equals(r.id());
        if (isStale(target.getUpdatedAt(), r.updatedAt())) {
            return remapped ? new RowOutcome.Remapped(r.id(), target.getId(), false) : stale(r.id());
        }

        target.setClientUpdatedAt(r.updatedAt());
        target.setDeletedAt(r.deletedAt());
        target.apply(
                r.foodName().trim(),
                r.brand() == null || r.brand().isBlank() ? null : r.brand().trim(),
                r.kcalPer100g(),
                r.proteinPer100g(),
                r.carbsPer100g(),
                r.fatPer100g(),
                FoodMapper.validUnits(r.servingUnits()));
        favorites.saveAndFlush(target);
        return remapped ? new RowOutcome.Remapped(r.id(), target.getId(), true) : accepted(r.id());
    }

    @Override
    public List<PulledRow> pull(UUID userId, Instant since, int limit) {
        return favorites
                .findByUserIdAndSyncedAtGreaterThanEqualOrderBySyncedAtAscIdAsc(
                        userId, since, PageRequest.of(0, limit))
                .stream()
                .map(
                        f ->
                                new PulledRow(
                                        f.getSyncedAt(),
                                        toNode(
                                                new FavoriteRow(
                                                        f.getId(),
                                                        f.getUserId(),
                                                        f.getUpdatedAt(),
                                                        f.getDeletedAt(),
                                                        f.getFoodRef(),
                                                        f.getFoodName(),
                                                        f.getBrand(),
                                                        f.getKcalPer100g(),
                                                        f.getProteinPer100g(),
                                                        f.getCarbsPer100g(),
                                                        f.getFatPer100g(),
                                                        f.getServingUnits()))))
                .toList();
    }
}
