package com.example.fitapp.profile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fitapp.support.AbstractIntegrationTest;
import com.example.fitapp.support.TestUsers.Session;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class ProfileIntegrationTest extends AbstractIntegrationTest {

    private static Map<String, Object> validProfile(String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("age", 30);
        m.put("sex", "MALE");
        m.put("heightCm", 180.0);
        m.put("weightKg", 80.0);
        return m;
    }

    private ResultActions putProfile(Session s, Map<String, Object> body) throws Exception {
        return mvc.perform(
                put("/api/me/profile")
                        .header("Authorization", s.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)));
    }

    private ResultActions putTargets(Session s, Map<String, Object> body) throws Exception {
        return mvc.perform(
                put("/api/me/targets")
                        .header("Authorization", s.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)));
    }

    private ResultActions suggest(Session s, String body) throws Exception {
        var req = post("/api/me/targets/suggest").header("Authorization", s.bearer());
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(req);
    }

    @Test
    void updatingProfileMarksItComplete() throws Exception {
        Session s = users().register("Before");
        Map<String, Object> body = validProfile("After");
        body.put("goal", "MUSCLE_GAIN");
        body.put("fitnessLevel", "INTERMEDIATE");
        body.put("timezone", "Asia/Karachi");
        body.put("currency", "pkr");
        body.put("units", "IMPERIAL");

        putProfile(s, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("After"))
                .andExpect(jsonPath("$.profileComplete").value(true))
                .andExpect(jsonPath("$.goal").value("MUSCLE_GAIN"))
                .andExpect(jsonPath("$.fitnessLevel").value("INTERMEDIATE"))
                .andExpect(jsonPath("$.timezone").value("Asia/Karachi"))
                .andExpect(jsonPath("$.currency").value("PKR"))
                .andExpect(jsonPath("$.units").value("IMPERIAL"));
    }

    @Test
    void omittedOptionalFieldsKeepTheirValues() throws Exception {
        Session s = users().register("Keeper");
        Map<String, Object> first = validProfile("Keeper");
        first.put("goal", "FAT_LOSS");
        putProfile(s, first).andExpect(status().isOk());

        putProfile(s, validProfile("Keeper"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goal").value("FAT_LOSS"));
    }

    @Test
    void profileValidationReturnsFieldErrors() throws Exception {
        Session s = users().register("Invalid");
        Map<String, Object> body = validProfile("Invalid");
        body.put("age", 5);
        body.put("weightKg", 900.0);
        body.put("timezone", "Mars/Olympus");

        putProfile(s, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.age").exists())
                .andExpect(jsonPath("$.errors.weightKg").exists());

        body.put("age", 30);
        body.put("weightKg", 80.0);
        putProfile(s, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.timezone").exists());

        body.put("timezone", "UTC");
        body.put("currency", "ZZZ");
        putProfile(s, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currency").exists());
    }

    @Test
    void missingRequiredProfileFieldsAreRejected() throws Exception {
        Session s = users().register("Partial");
        putProfile(s, Map.of("name", "Partial"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.age").exists())
                .andExpect(jsonPath("$.errors.sex").exists())
                .andExpect(jsonPath("$.errors.heightCm").exists())
                .andExpect(jsonPath("$.errors.weightKg").exists());
    }

    @Test
    void targetsAreNotFoundUntilSet() throws Exception {
        Session s = users().register("NoTargets");
        mvc.perform(get("/api/me/targets").header("Authorization", s.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void suggestNeedsACompleteProfileOrOverrides() throws Exception {
        Session s = users().register("Incomplete");
        suggest(s, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.age").exists());
    }

    @Test
    void suggestUsesTheSavedProfile() throws Exception {
        Session s = users().register("Saved");
        Map<String, Object> body = validProfile("Saved");
        body.put("goal", "MUSCLE_GAIN");
        body.put("activityLevel", "MODERATE");
        putProfile(s, body).andExpect(status().isOk());

        suggest(s, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bmr").value(1780))
                .andExpect(jsonPath("$.tdee").value(2759))
                .andExpect(jsonPath("$.calories").value(3104))
                .andExpect(jsonPath("$.proteinG").value(144))
                .andExpect(jsonPath("$.fatG").value(86))
                .andExpect(jsonPath("$.carbsG").value(439))
                .andExpect(jsonPath("$.waterGlasses").value(12));
    }

    @Test
    void suggestAcceptsOverridesBeforeTheProfileIsSaved() throws Exception {
        Session s = users().register("Onboarding");
        suggest(
                        s,
                        """
                        {"age":30,"sex":"MALE","heightCm":180,"weightKg":80,
                         "activityLevel":"MODERATE","goal":"MAINTENANCE"}
                        """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calories").value(2759));
    }

    @Test
    void suggestDoesNotPersistAnything() throws Exception {
        Session s = users().register("Preview");
        suggest(s, "{\"age\":30,\"sex\":\"MALE\",\"heightCm\":180,\"weightKg\":80}")
                .andExpect(status().isOk());
        mvc.perform(get("/api/me/targets").header("Authorization", s.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void userCanOverrideTargetsAndTheLatestWins() throws Exception {
        Session s = users().register("Override");
        Map<String, Object> targets = new LinkedHashMap<>();
        targets.put("calories", 2400);
        targets.put("proteinG", 160);
        targets.put("carbsG", 250);
        targets.put("fatG", 70);
        targets.put("waterGlasses", 10);

        putTargets(s, targets)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calories").value(2400));

        // second PUT on the same day updates in place
        targets.put("calories", 2600);
        putTargets(s, targets)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calories").value(2600));

        mvc.perform(get("/api/me/targets").header("Authorization", s.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calories").value(2600))
                .andExpect(jsonPath("$.proteinG").value(160))
                .andExpect(jsonPath("$.waterGlasses").value(10));
    }

    @Test
    void futureDatedTargetsDoNotApplyYet() throws Exception {
        Session s = users().register("Future");
        Map<String, Object> targets = new LinkedHashMap<>();
        targets.put("calories", 2000);
        targets.put("proteinG", 150);
        targets.put("carbsG", 200);
        targets.put("fatG", 60);
        targets.put("waterGlasses", 8);
        targets.put("effectiveFrom", "2999-01-01");
        putTargets(s, targets).andExpect(status().isOk());

        mvc.perform(get("/api/me/targets").header("Authorization", s.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void targetValidationReturnsFieldErrors() throws Exception {
        Session s = users().register("BadTargets");
        Map<String, Object> targets = new LinkedHashMap<>();
        targets.put("calories", 100);
        targets.put("proteinG", 150);
        targets.put("carbsG", 200);
        targets.put("fatG", 60);
        targets.put("waterGlasses", 8);
        putTargets(s, targets)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.calories").exists());
    }

    @Test
    void usersNeverSeeEachOthersProfileOrTargets() throws Exception {
        Session alice = users().register("Alice Private");
        Session bob = users().register("Bob Private");

        Map<String, Object> aliceProfile = validProfile("Alice Private");
        aliceProfile.put("weightKg", 61.5);
        putProfile(alice, aliceProfile).andExpect(status().isOk());
        Map<String, Object> aliceTargets = new LinkedHashMap<>();
        aliceTargets.put("calories", 1800);
        aliceTargets.put("proteinG", 120);
        aliceTargets.put("carbsG", 200);
        aliceTargets.put("fatG", 50);
        aliceTargets.put("waterGlasses", 8);
        putTargets(alice, aliceTargets).andExpect(status().isOk());

        // Bob only ever gets his own data: the id comes from his JWT, there is no way to ask for Alice's.
        mvc.perform(get("/api/me/profile").header("Authorization", bob.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bob Private"))
                .andExpect(jsonPath("$.profileComplete").value(false))
                .andExpect(jsonPath("$.weightKg").doesNotExist());
        mvc.perform(get("/api/me/targets").header("Authorization", bob.bearer()))
                .andExpect(status().isNotFound());
    }
}
