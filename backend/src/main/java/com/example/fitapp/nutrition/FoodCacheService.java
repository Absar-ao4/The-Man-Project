package com.example.fitapp.nutrition;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Writes external foods into the shared cache. Kept separate so a failed write cannot spoil a search. */
@Service
public class FoodCacheService {

    private final FoodRepository foods;

    public FoodCacheService(FoodRepository foods) {
        this.foods = foods;
    }

    @Transactional
    public void upsertAll(List<FoodData> items) {
        for (FoodData data : items) {
            Food food =
                    foods.findBySourceAndExternalId(data.source(), data.externalId())
                            .orElseGet(() -> new Food(UUID.randomUUID(), data.source(), data.externalId()));
            food.apply(data);
            foods.save(food);
        }
        foods.flush();
    }
}
