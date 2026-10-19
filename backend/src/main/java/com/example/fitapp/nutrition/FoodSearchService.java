package com.example.fitapp.nutrition;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.nutrition.dto.NutritionDtos.FoodDto;
import com.example.fitapp.nutrition.dto.NutritionDtos.FoodSearchResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Food search and barcode lookup. The user's own foods always come first. External results are
 * cached; when the external service is down the cache answers instead of failing the request.
 */
@Service
public class FoodSearchService {

    private static final Logger log = LoggerFactory.getLogger(FoodSearchService.class);
    private static final Pattern BARCODE = Pattern.compile("^[0-9]{6,14}$");
    private static final int MIN_QUERY_LENGTH = 2;
    private static final int MAX_QUERY_LENGTH = 100;
    private static final int CUSTOM_LIMIT = 10;
    private static final int CACHE_LIMIT = 25;

    private final UsdaClient usda;
    private final OpenFoodFactsClient openFoodFacts;
    private final FoodCacheService cache;
    private final FoodRepository foods;
    private final CustomFoodRepository customFoods;

    public FoodSearchService(
            UsdaClient usda,
            OpenFoodFactsClient openFoodFacts,
            FoodCacheService cache,
            FoodRepository foods,
            CustomFoodRepository customFoods) {
        this.usda = usda;
        this.openFoodFacts = openFoodFacts;
        this.cache = cache;
        this.foods = foods;
        this.customFoods = customFoods;
    }

    public FoodSearchResponse search(UUID userId, String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.length() < MIN_QUERY_LENGTH || query.length() > MAX_QUERY_LENGTH) {
            throw ApiException.validation(
                    "q", "must be between " + MIN_QUERY_LENGTH + " and " + MAX_QUERY_LENGTH + " characters");
        }

        List<FoodDto> items = new ArrayList<>();
        customFoods
                .search(userId, FoodMapper.likePattern(query), PageRequest.of(0, CUSTOM_LIMIT))
                .forEach(c -> items.add(FoodMapper.toFoodDto(c)));

        try {
            List<FoodData> live = usda.search(query);
            storeInCache(live);
            live.forEach(d -> items.add(FoodMapper.toDto(d)));
            return new FoodSearchResponse(items, "LIVE");
        } catch (ApiException e) {
            if (e.code() != ErrorCode.UPSTREAM_UNAVAILABLE) {
                throw e;
            }
            List<Food> cached = searchCache(query);
            if (cached.isEmpty() && items.isEmpty()) {
                throw e;
            }
            cached.forEach(f -> items.add(FoodMapper.toDto(f.toData())));
            return new FoodSearchResponse(items, "CACHE");
        }
    }

    public FoodDto byBarcode(String code) {
        String barcode = code == null ? "" : code.trim();
        if (!BARCODE.matcher(barcode).matches()) {
            throw ApiException.validation("code", "must be 6 to 14 digits");
        }
        try {
            FoodData found =
                    openFoodFacts
                            .byBarcode(barcode)
                            .orElseThrow(() -> ApiException.notFound("No product with that barcode"));
            storeInCache(List.of(found));
            return FoodMapper.toDto(found);
        } catch (ApiException e) {
            if (e.code() != ErrorCode.UPSTREAM_UNAVAILABLE) {
                throw e;
            }
            return foods.findBySourceAndExternalId("OFF", barcode)
                    .map(f -> FoodMapper.toDto(f.toData()))
                    .orElseThrow(() -> e);
        }
    }

    @Transactional(readOnly = true)
    List<Food> searchCache(String query) {
        return foods.searchCached(FoodMapper.likePattern(query), PageRequest.of(0, CACHE_LIMIT));
    }

    /** Best effort: a cache write failing (for example a concurrent insert) must not fail the search. */
    private void storeInCache(List<FoodData> items) {
        if (items.isEmpty()) {
            return;
        }
        try {
            cache.upsertAll(items);
        } catch (DataAccessException e) {
            log.warn("Could not update the food cache: {}", e.getClass().getSimpleName());
        }
    }
}
