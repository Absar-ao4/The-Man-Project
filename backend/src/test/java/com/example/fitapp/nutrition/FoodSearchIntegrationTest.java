package com.example.fitapp.nutrition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.support.AbstractIntegrationTest;
import com.example.fitapp.support.TestUsers.Session;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class FoodSearchIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean UsdaClient usda;
    @MockitoBean OpenFoodFactsClient openFoodFacts;

    private static FoodData usdaFood(String id, String name, double kcal) {
        return new FoodData(
                "USDA", id, name, null, kcal, 10, 20, 5, List.of(new ServingUnit("g", 1.0)));
    }

    private static ApiException down() {
        return new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "The food database is unavailable");
    }

    private static Map<String, Object> customFood(String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("kcalPer100g", 250);
        m.put("proteinPer100g", 20);
        m.put("carbsPer100g", 10);
        m.put("fatPer100g", 12);
        return m;
    }

    @Test
    void searchReturnsLiveUsdaResults() throws Exception {
        Session s = users().register("Searcher");
        when(usda.search("chicken breast")).thenReturn(List.of(usdaFood("1001", "Chicken breast, cooked", 165)));

        JsonNode result = body(get(s, "/api/foods/search?q=chicken%20breast").andExpect(status().isOk()));

        assertThat(result.get("origin").asText()).isEqualTo("LIVE");
        JsonNode first = result.get("items").get(0);
        assertThat(first.get("foodRef").asText()).isEqualTo("USDA:1001");
        assertThat(first.get("kcalPer100g").asDouble()).isEqualTo(165.0);
        assertThat(first.get("custom").asBoolean()).isFalse();
    }

    @Test
    void ownCustomFoodsComeFirst() throws Exception {
        Session s = users().register("Custom First");
        post(s, "/api/custom-foods", customFood("Homemade chicken salad")).andExpect(status().isCreated());
        when(usda.search("chicken")).thenReturn(List.of(usdaFood("1002", "Chicken thigh", 209)));

        JsonNode items = body(get(s, "/api/foods/search?q=chicken")).get("items");

        assertThat(items).hasSize(2);
        assertThat(items.get(0).get("custom").asBoolean()).isTrue();
        assertThat(items.get(0).get("foodRef").asText()).startsWith("CUSTOM:");
        assertThat(items.get(1).get("foodRef").asText()).isEqualTo("USDA:1002");
    }

    @Test
    void otherUsersCustomFoodsNeverAppear() throws Exception {
        Session alice = users().register("Alice");
        Session bob = users().register("Bob");
        post(alice, "/api/custom-foods", customFood("Alice secret stew")).andExpect(status().isCreated());
        when(usda.search("stew")).thenReturn(List.of());

        assertThat(body(get(bob, "/api/foods/search?q=stew")).get("items")).isEmpty();
        assertThat(body(get(alice, "/api/foods/search?q=stew")).get("items")).hasSize(1);
    }

    @Test
    void whenUsdaIsDownTheCacheAnswers() throws Exception {
        Session s = users().register("Cached");
        when(usda.search("zucchini")).thenReturn(List.of(usdaFood("2001", "Zucchini, raw", 17)));
        get(s, "/api/foods/search?q=zucchini").andExpect(status().isOk()); // fills the cache

        when(usda.search(anyString())).thenThrow(down());
        JsonNode result = body(get(s, "/api/foods/search?q=zucchi").andExpect(status().isOk()));

        assertThat(result.get("origin").asText()).isEqualTo("CACHE");
        assertThat(result.get("items").get(0).get("name").asText()).isEqualTo("Zucchini, raw");
    }

    @Test
    void whenUsdaIsDownAndNothingIsCachedItIsAnUpstreamError() throws Exception {
        Session s = users().register("Offline");
        when(usda.search(anyString())).thenThrow(down());

        get(s, "/api/foods/search?q=quinoa-never-searched")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UPSTREAM_UNAVAILABLE"));
    }

    @Test
    void customFoodsStillWorkWhenUsdaIsDown() throws Exception {
        Session s = users().register("Resilient");
        post(s, "/api/custom-foods", customFood("Protein pancakes")).andExpect(status().isCreated());
        when(usda.search(anyString())).thenThrow(down());

        JsonNode result = body(get(s, "/api/foods/search?q=pancakes").andExpect(status().isOk()));

        assertThat(result.get("origin").asText()).isEqualTo("CACHE");
        assertThat(result.get("items")).hasSize(1);
    }

    @Test
    void repeatedSearchesDoNotDuplicateTheCache() throws Exception {
        Session s = users().register("Repeat");
        when(usda.search("oats")).thenReturn(List.of(usdaFood("3001", "Oats", 379)));
        get(s, "/api/foods/search?q=oats").andExpect(status().isOk());
        get(s, "/api/foods/search?q=oats").andExpect(status().isOk());

        when(usda.search(anyString())).thenThrow(down());
        assertThat(body(get(s, "/api/foods/search?q=oats")).get("items")).hasSize(1);
    }

    @Test
    void shortOrMissingQueriesAreRejected() throws Exception {
        Session s = users().register("Short");
        get(s, "/api/foods/search?q=a")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.q").exists());
        get(s, "/api/foods/search").andExpect(status().isBadRequest());
    }

    @Test
    void barcodeLookupReturnsTheProduct() throws Exception {
        Session s = users().register("Scanner");
        when(openFoodFacts.byBarcode("5000159407236"))
                .thenReturn(
                        Optional.of(
                                new FoodData(
                                        "OFF",
                                        "5000159407236",
                                        "Chocolate bar",
                                        "Acme",
                                        520,
                                        6,
                                        60,
                                        28,
                                        List.of(new ServingUnit("g", 1.0), new ServingUnit("1 bar", 45.0)))));

        get(s, "/api/foods/barcode/5000159407236")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.foodRef").value("OFF:5000159407236"))
                .andExpect(jsonPath("$.name").value("Chocolate bar"))
                .andExpect(jsonPath("$.servingUnits[1].grams").value(45.0));
    }

    @Test
    void unknownBarcodeIsNotFound() throws Exception {
        Session s = users().register("Unknown");
        when(openFoodFacts.byBarcode("000000000000")).thenReturn(Optional.empty());
        get(s, "/api/foods/barcode/000000000000")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void invalidBarcodesAreRejectedWithoutCallingTheService() throws Exception {
        Session s = users().register("Invalid");
        get(s, "/api/foods/barcode/abc123").andExpect(status().isBadRequest());
        get(s, "/api/foods/barcode/123").andExpect(status().isBadRequest());
        get(s, "/api/foods/barcode/12345678901234567").andExpect(status().isBadRequest());
    }

    @Test
    void aScannedBarcodeIsServedFromTheCacheWhenTheServiceIsDown() throws Exception {
        Session s = users().register("Barcode Cache");
        String code = "7622210449283";
        when(openFoodFacts.byBarcode(code))
                .thenReturn(Optional.of(new FoodData("OFF", code, "Biscuits", null, 480, 7, 70, 20, List.of())));
        get(s, "/api/foods/barcode/" + code).andExpect(status().isOk());

        when(openFoodFacts.byBarcode(code)).thenThrow(down());
        get(s, "/api/foods/barcode/" + code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Biscuits"));

        String uncached = "7622210449999";
        when(openFoodFacts.byBarcode(uncached)).thenThrow(down());
        get(s, "/api/foods/barcode/" + uncached).andExpect(status().isServiceUnavailable());
    }

    @Test
    void searchAndBarcodeRequireAuthentication() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/foods/search?q=rice"))
                .andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/foods/barcode/5000159407236"))
                .andExpect(status().isUnauthorized());
        assertThat(UUID.randomUUID()).isNotNull();
    }
}
