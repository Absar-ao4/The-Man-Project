package com.example.fitapp.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fitapp.ai.AiModelClient.AiRequest;
import com.example.fitapp.ai.AiModelClient.RawReply;
import com.example.fitapp.common.ApiException;
import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.nutrition.FoodData;
import com.example.fitapp.nutrition.UsdaClient;
import com.example.fitapp.support.AbstractIntegrationTest;
import com.example.fitapp.support.TestUsers;
import com.example.fitapp.support.TestUsers.Session;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** The scan endpoint end to end with the AI model and USDA replaced by mocks. */
@TestPropertySource(properties = "app.anthropic.api-key=test-key")
class PhotoScanIntegrationTest extends AbstractIntegrationTest {

    /** A tiny but valid JPEG header is enough: the server checks the magic bytes, the mock model never looks. */
    private static final String JPEG_BASE64 =
            java.util.Base64.getEncoder().encodeToString(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'});

    @MockitoBean AiModelClient ai;
    @MockitoBean UsdaClient usda;
    @Autowired PhotoScanConfigService config;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void defaults() {
        config.update(new PhotoScanConfig(true, 2, 5, new BigDecimal("1000"), "claude-haiku-5-5", "claude-sonnet-5-5", 0.5));
        jdbc.update("delete from photo_scan_budget");
        jdbc.update("delete from photo_scan_logs");
        when(usda.search(anyString())).thenReturn(List.of());
    }

    // ------------------------------------------------------------ helpers

    private ObjectNode foods(double confidence, String... namesAndKcal) {
        ObjectNode n = json.createObjectNode();
        n.put("isFood", true);
        n.put("notes", "Portions are rough");
        var items = n.putArray("items");
        for (int i = 0; i < namesAndKcal.length; i += 2) {
            ObjectNode item = items.addObject();
            item.put("name", namesAndKcal[i]);
            item.put("estimatedGrams", 200);
            item.put("confidence", confidence);
            item.put("calories", Double.parseDouble(namesAndKcal[i + 1]));
            item.put("proteinG", 10);
            item.put("carbsG", 40);
            item.put("fatG", 5);
        }
        return n;
    }

    private void modelAnswers(ObjectNode toolInput) {
        when(ai.call(any(AiRequest.class))).thenReturn(new RawReply(toolInput, 1500, 300));
    }

    private Map<String, Object> scanBody() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("imageBase64", JPEG_BASE64);
        m.put("mediaType", "image/jpeg");
        m.put("mealHint", "lunch");
        return m;
    }

    private static FoodData usdaFood(String id, String name, double kcalPer100g) {
        return new FoodData("USDA", id, name, null, kcalPer100g, 3, 28, 0.3, List.of());
    }

    private Session adminSession() throws Exception {
        Session user = users().register("Photo Admin");
        jdbc.update("update users set role = 'ADMIN' where id = ?", user.userId());
        String response =
                mvc.perform(
                                MockMvcRequestBuilders.post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json.writeValueAsString(Map.of("email", user.email(), "password", TestUsers.PASSWORD))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return TestUsers.toSession(json.readTree(response));
    }

    // ------------------------------------------------------------ happy path

    @Test
    void aScanReturnsUsdaMatchesAndAiEstimatesAndCountsAgainstTheQuota() throws Exception {
        Session s = users().register("Scanner");
        // Model: rice 200 g = 260 kcal (130 per 100 g). Curry 200 g = 300 kcal (150 per 100 g).
        modelAnswers(foods(0.9, "white rice, cooked", "260", "home style curry", "300"));
        when(usda.search("white rice, cooked")).thenReturn(List.of(usdaFood("168878", "Rice, white, cooked", 130)));
        when(usda.search("home style curry")).thenReturn(List.of(usdaFood("999", "Cheese puffs", 540))); // implausible

        JsonNode result = body(post(s, "/api/nutrition/photo-scan", scanBody()).andExpect(status().isOk()));

        assertThat(result.get("isFood").asBoolean()).isTrue();
        assertThat(result.get("scansLeftToday").asInt()).isEqualTo(1);
        assertThat(result.get("plan").asText()).isEqualTo("FREE");
        JsonNode rice = result.get("items").get(0);
        assertThat(rice.get("source").asText()).isEqualTo("USDA_MATCH");
        assertThat(rice.get("matchedName").asText()).isEqualTo("Rice, white, cooked");
        assertThat(rice.get("foodRef").asText()).isEqualTo("USDA:168878");
        assertThat(rice.get("kcalPer100g").asDouble()).isEqualTo(130.0);
        JsonNode curry = result.get("items").get(1);
        assertThat(curry.get("source").asText()).isEqualTo("AI_ESTIMATE");
        assertThat(curry.get("kcalPer100g").asDouble()).isEqualTo(150.0);
        assertThat(curry.has("matchedName")).isFalse();

        // Metered: one budget call and one log row, with numbers only.
        assertThat(jdbc.queryForObject("select calls from photo_scan_budget", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select spend_usd from photo_scan_budget", BigDecimal.class)).isGreaterThan(BigDecimal.ZERO);
        assertThat(jdbc.queryForObject("select count(*) from photo_scan_logs where kind = 'PHOTO' and status = 'OK' and items = 2", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void theRequestToTheModelCarriesTheImageTheHintAndTheForcedTool() throws Exception {
        Session s = users().register("Request Shape");
        modelAnswers(foods(0.9, "egg", "140"));

        post(s, "/api/nutrition/photo-scan", scanBody()).andExpect(status().isOk());

        ArgumentCaptor<AiRequest> captor = ArgumentCaptor.forClass(AiRequest.class);
        verify(ai, times(1)).call(captor.capture());
        AiRequest sent = captor.getValue();
        assertThat(sent.model()).isEqualTo("claude-haiku-5-5");
        assertThat(sent.toolName()).isEqualTo("report_foods");
        assertThat(sent.content().get(0).get("type").asText()).isEqualTo("image");
        assertThat(sent.content().get(0).get("source").get("media_type").asText()).isEqualTo("image/jpeg");
        assertThat(sent.content().get(1).get("text").asText()).contains("lunch").contains("untrusted");
    }

    @Test
    void aPhotoThatIsNotFoodReturnsNoItems() throws Exception {
        Session s = users().register("Not Food");
        ObjectNode n = json.createObjectNode();
        n.put("isFood", false);
        n.putArray("items");
        modelAnswers(n);

        post(s, "/api/nutrition/photo-scan", scanBody())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isFood").value(false))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void whenUsdaIsDownTheModelEstimateIsUsed() throws Exception {
        Session s = users().register("Usda Down");
        modelAnswers(foods(0.9, "banana", "180"));
        when(usda.search(anyString())).thenThrow(new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "down"));

        JsonNode result = body(post(s, "/api/nutrition/photo-scan", scanBody()).andExpect(status().isOk()));

        assertThat(result.get("items").get(0).get("source").asText()).isEqualTo("AI_ESTIMATE");
    }

    // ------------------------------------------------------------ escalation

    @Test
    void lowConfidenceEscalatesToTheFallbackModelOnce() throws Exception {
        Session s = users().register("Escalate");
        when(ai.call(any(AiRequest.class)))
                .thenReturn(new RawReply(foods(0.2, "something", "200"), 1000, 200))
                .thenReturn(new RawReply(foods(0.9, "grilled chicken", "330"), 1000, 200));

        JsonNode result = body(post(s, "/api/nutrition/photo-scan", scanBody()).andExpect(status().isOk()));

        assertThat(result.get("items").get(0).get("name").asText()).isEqualTo("grilled chicken");
        ArgumentCaptor<AiRequest> captor = ArgumentCaptor.forClass(AiRequest.class);
        verify(ai, times(2)).call(captor.capture());
        assertThat(captor.getAllValues().get(1).model()).isEqualTo("claude-sonnet-5-5");
        assertThat(jdbc.queryForObject("select count(*) from photo_scan_logs where fallback_used", Integer.class)).isEqualTo(1);
        // One scan for the user, two calls on the budget.
        assertThat(jdbc.queryForObject("select calls from photo_scan_budget", Integer.class)).isEqualTo(2);
        assertThat(result.get("scansLeftToday").asInt()).isEqualTo(1);
    }

    @Test
    void ifTheFallbackFailsTheFirstAnswerIsKept() throws Exception {
        Session s = users().register("Fallback Fails");
        when(ai.call(any(AiRequest.class)))
                .thenReturn(new RawReply(foods(0.2, "something", "200"), 1000, 200))
                .thenThrow(new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "down"));

        JsonNode result = body(post(s, "/api/nutrition/photo-scan", scanBody()).andExpect(status().isOk()));

        assertThat(result.get("items").get(0).get("name").asText()).isEqualTo("something");
    }

    @Test
    void confidentAnswersNeverCallTheFallback() throws Exception {
        Session s = users().register("Confident");
        modelAnswers(foods(0.8, "oats", "150"));

        post(s, "/api/nutrition/photo-scan", scanBody()).andExpect(status().isOk());

        verify(ai, times(1)).call(any(AiRequest.class));
    }

    // ------------------------------------------------------------ limits and failures

    @Test
    void thePerUserDailyLimitReturns429AndPremiumGetsMore() throws Exception {
        Session free = users().register("Free Limited");
        modelAnswers(foods(0.9, "egg", "140"));

        post(free, "/api/nutrition/photo-scan", scanBody()).andExpect(status().isOk());
        post(free, "/api/nutrition/photo-scan", scanBody()).andExpect(status().isOk()).andExpect(jsonPath("$.scansLeftToday").value(0));
        post(free, "/api/nutrition/photo-scan", scanBody()).andExpect(status().isTooManyRequests());

        Session premium = users().register("Premium Scanner");
        jdbc.update("update users set plan = 'PREMIUM' where id = ?", premium.userId());
        for (int i = 0; i < 5; i++) {
            post(premium, "/api/nutrition/photo-scan", scanBody()).andExpect(status().isOk());
        }
        post(premium, "/api/nutrition/photo-scan", scanBody()).andExpect(status().isTooManyRequests());
    }

    @Test
    void aFailedModelCallIsRefundedAndNotCounted() throws Exception {
        Session s = users().register("Refund");
        when(ai.call(any(AiRequest.class))).thenThrow(new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "down"));

        post(s, "/api/nutrition/photo-scan", scanBody()).andExpect(status().isServiceUnavailable());
        post(s, "/api/nutrition/photo-scan", scanBody()).andExpect(status().isServiceUnavailable());
        post(s, "/api/nutrition/photo-scan", scanBody()).andExpect(status().isServiceUnavailable());

        modelAnswers(foods(0.9, "egg", "140"));
        post(s, "/api/nutrition/photo-scan", scanBody()).andExpect(status().isOk()).andExpect(jsonPath("$.scansLeftToday").value(1));
        assertThat(jdbc.queryForObject("select count(*) from photo_scan_logs where status = 'ERROR'", Integer.class)).isEqualTo(3);
    }

    @Test
    void aRateLimitedProviderAnswers429() throws Exception {
        Session s = users().register("Busy Provider");
        when(ai.call(any(AiRequest.class))).thenThrow(new ApiException(ErrorCode.LIMIT_REACHED, "busy, try again in a minute"));

        post(s, "/api/nutrition/photo-scan", scanBody()).andExpect(status().isTooManyRequests());
    }

    @Test
    void anExhaustedGlobalBudgetStopsScansBeforeAnyModelCall() throws Exception {
        Session s = users().register("Budget Hit");
        config.update(new PhotoScanConfig(true, 2, 5, BigDecimal.ZERO, "claude-haiku-5-5", "claude-sonnet-5-5", 0.5));

        post(s, "/api/nutrition/photo-scan", scanBody()).andExpect(status().isServiceUnavailable());

        verify(ai, org.mockito.Mockito.never()).call(any(AiRequest.class));
    }

    @Test
    void theBudgetIsSharedSoSpendFromOneCallCanStopTheNext() throws Exception {
        Session s = users().register("Shared Budget");
        modelAnswers(foods(0.9, "egg", "140"));
        post(s, "/api/nutrition/photo-scan", scanBody()).andExpect(status().isOk());
        BigDecimal spent = jdbc.queryForObject("select spend_usd from photo_scan_budget", BigDecimal.class);
        // A budget just below what one scan cost: the next scan is refused.
        config.update(new PhotoScanConfig(true, 5, 5, spent, "claude-haiku-5-5", "claude-sonnet-5-5", 0.5));

        post(s, "/api/nutrition/photo-scan", scanBody()).andExpect(status().isServiceUnavailable());
    }

    @Test
    void switchingTheFeatureOffReturns503() throws Exception {
        Session s = users().register("Switched Off");
        config.update(new PhotoScanConfig(false, 2, 5, new BigDecimal("1000"), "claude-haiku-5-5", "claude-sonnet-5-5", 0.5));

        post(s, "/api/nutrition/photo-scan", scanBody()).andExpect(status().isServiceUnavailable());
    }

    @Test
    void aLimitOfZeroMeansNoScansForThatPlan() throws Exception {
        Session s = users().register("Zero Limit");
        config.update(new PhotoScanConfig(true, 0, 5, new BigDecimal("1000"), "claude-haiku-5-5", "claude-sonnet-5-5", 0.5));

        post(s, "/api/nutrition/photo-scan", scanBody()).andExpect(status().isTooManyRequests());
    }

    // ------------------------------------------------------------ validation and auth

    @Test
    void badInputIsRejectedBeforeAnythingIsSpent() throws Exception {
        Session s = users().register("Bad Input");

        Map<String, Object> gif = scanBody();
        gif.put("mediaType", "image/gif");
        post(s, "/api/nutrition/photo-scan", gif).andExpect(status().isBadRequest());

        Map<String, Object> notBase64 = scanBody();
        notBase64.put("imageBase64", "!!! not base64 !!!");
        post(s, "/api/nutrition/photo-scan", notBase64).andExpect(status().isBadRequest());

        Map<String, Object> wrongBytes = scanBody();
        wrongBytes.put("imageBase64", java.util.Base64.getEncoder().encodeToString("this is plain text, not a jpeg".getBytes()));
        post(s, "/api/nutrition/photo-scan", wrongBytes).andExpect(status().isBadRequest());

        Map<String, Object> tooBig = scanBody();
        byte[] big = new byte[4 * 1024 * 1024 + 10];
        big[0] = (byte) 0xFF;
        big[1] = (byte) 0xD8;
        big[2] = (byte) 0xFF;
        tooBig.put("imageBase64", java.util.Base64.getEncoder().encodeToString(big));
        post(s, "/api/nutrition/photo-scan", tooBig).andExpect(status().isBadRequest());

        Map<String, Object> longHint = scanBody();
        longHint.put("mealHint", "x".repeat(201));
        post(s, "/api/nutrition/photo-scan", longHint).andExpect(status().isBadRequest());

        Map<String, Object> missing = new LinkedHashMap<>();
        missing.put("mediaType", "image/jpeg");
        post(s, "/api/nutrition/photo-scan", missing).andExpect(status().isBadRequest());

        verify(ai, org.mockito.Mockito.never()).call(any(AiRequest.class));
        assertThat(jdbc.queryForObject("select count(*) from photo_scan_usage where user_id = ?", Integer.class, s.userId())).isZero();
    }

    @Test
    void signedOutRequestsAreRejected() throws Exception {
        mvc.perform(
                        MockMvcRequestBuilders.post("/api/nutrition/photo-scan")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(scanBody())))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ admin

    @Test
    void onlyAdminsCanReadOrChangeTheConfigAndStats() throws Exception {
        Session user = users().register("Plain User");

        get(user, "/api/admin/photo-scan/config").andExpect(status().isForbidden());
        get(user, "/api/admin/photo-scan/stats").andExpect(status().isForbidden());
        put(user, "/api/admin/photo-scan/config", Map.of()).andExpect(status().isForbidden());
    }

    @Test
    void anAdminCanChangeTheLimitsAndTheChangeAppliesAtOnce() throws Exception {
        Session admin = adminSession();
        JsonNode current = body(get(admin, "/api/admin/photo-scan/config").andExpect(status().isOk()));
        assertThat(current.get("freeDailyLimit").asInt()).isEqualTo(2);

        ObjectNode update = current.deepCopy();
        update.put("freeDailyLimit", 1);
        update.put("globalDailyBudgetUsd", 50);
        JsonNode saved = body(put(admin, "/api/admin/photo-scan/config", update).andExpect(status().isOk()));
        assertThat(saved.get("freeDailyLimit").asInt()).isEqualTo(1);

        Session s = users().register("One Scan");
        modelAnswers(foods(0.9, "egg", "140"));
        post(s, "/api/nutrition/photo-scan", scanBody()).andExpect(status().isOk());
        post(s, "/api/nutrition/photo-scan", scanBody()).andExpect(status().isTooManyRequests());
    }

    @Test
    void adminConfigValuesAreValidated() throws Exception {
        Session admin = adminSession();
        ObjectNode bad = body(get(admin, "/api/admin/photo-scan/config")).deepCopy();

        ObjectNode negative = bad.deepCopy();
        negative.put("freeDailyLimit", -1);
        put(admin, "/api/admin/photo-scan/config", negative).andExpect(status().isBadRequest());

        ObjectNode noModel = bad.deepCopy();
        noModel.put("primaryModel", " ");
        put(admin, "/api/admin/photo-scan/config", noModel).andExpect(status().isBadRequest());

        ObjectNode badThreshold = bad.deepCopy();
        badThreshold.put("escalateBelowConfidence", 1.5);
        put(admin, "/api/admin/photo-scan/config", badThreshold).andExpect(status().isBadRequest());

        ObjectNode negativeBudget = bad.deepCopy();
        negativeBudget.put("globalDailyBudgetUsd", -1);
        put(admin, "/api/admin/photo-scan/config", negativeBudget).andExpect(status().isBadRequest());
    }

    @Test
    void theStatsShowTodaysSpendScansAndFallbacks() throws Exception {
        Session admin = adminSession();
        Session s = users().register("Stats Scanner");
        modelAnswers(foods(0.9, "egg", "140"));
        post(s, "/api/nutrition/photo-scan", scanBody()).andExpect(status().isOk());

        JsonNode stats = body(get(admin, "/api/admin/photo-scan/stats").andExpect(status().isOk()));

        assertThat(stats.get("scansToday").asInt()).isEqualTo(1);
        assertThat(stats.get("callsToday").asInt()).isEqualTo(1);
        assertThat(stats.get("fallbackToday").asInt()).isZero();
        assertThat(stats.get("spentTodayUsd").asDouble()).isGreaterThan(0.0);
        assertThat(stats.get("budgetUsd").asDouble()).isEqualTo(1000.0);
        assertThat(stats.get("averageConfidenceToday").asDouble()).isEqualTo(0.9, org.assertj.core.data.Offset.offset(0.001));
        assertThat(stats.get("lastSevenDays")).hasSize(1);
    }
}
