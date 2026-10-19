package com.example.fitapp.nutrition;

import java.util.Optional;

/** Looks products up by barcode. Empty means "no such product"; failures throw UPSTREAM_UNAVAILABLE. */
public interface OpenFoodFactsClient {

    Optional<FoodData> byBarcode(String barcode);
}
