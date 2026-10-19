package com.example.fitapp.nutrition;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.nutrition.dto.NutritionDtos.FavoriteDto;
import com.example.fitapp.nutrition.dto.NutritionDtos.FavoriteRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FavoriteService {

    private final FavoriteFoodRepository favorites;

    public FavoriteService(FavoriteFoodRepository favorites) {
        this.favorites = favorites;
    }

    @Transactional(readOnly = true)
    public List<FavoriteDto> list(UUID userId) {
        return favorites.findByUserIdAndDeletedAtIsNullOrderByFoodNameAsc(userId).stream()
                .map(FoodMapper::toDto)
                .toList();
    }

    /** Favoriting the same food twice just refreshes the stored snapshot. */
    @Transactional
    public FavoriteDto add(UUID userId, FavoriteRequest req) {
        FavoriteFood favorite = favorites.findByUserIdAndFoodRef(userId, req.foodRef()).orElse(null);
        if (favorite == null) {
            UUID id = req.id() != null ? req.id() : UUID.randomUUID();
            if (favorites.existsById(id)) {
                throw new ApiException(ErrorCode.CONFLICT, "That id is already in use");
            }
            favorite = new FavoriteFood(id, userId, req.foodRef());
        } else {
            favorite.setDeletedAt(null);
        }
        favorite.apply(
                req.foodName().trim(),
                req.brand() == null || req.brand().isBlank() ? null : req.brand().trim(),
                req.kcalPer100g(),
                req.proteinPer100g(),
                req.carbsPer100g(),
                req.fatPer100g(),
                FoodMapper.validUnits(req.servingUnits()));
        return FoodMapper.toDto(favorites.saveAndFlush(favorite));
    }

    @Transactional
    public void remove(UUID userId, UUID id) {
        FavoriteFood favorite =
                favorites
                        .findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                        .orElseThrow(() -> ApiException.notFound("Favorite not found"));
        favorite.setDeletedAt(Instant.now());
        favorites.saveAndFlush(favorite);
    }
}
