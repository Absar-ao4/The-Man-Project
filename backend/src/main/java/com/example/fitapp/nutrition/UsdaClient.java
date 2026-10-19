package com.example.fitapp.nutrition;

import java.util.List;

/** Searches USDA FoodData Central. Throws an UPSTREAM_UNAVAILABLE ApiException when it cannot answer. */
public interface UsdaClient {

    List<FoodData> search(String query);
}
