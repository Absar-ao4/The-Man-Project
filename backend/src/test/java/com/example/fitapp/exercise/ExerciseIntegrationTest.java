package com.example.fitapp.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fitapp.support.AbstractIntegrationTest;
import com.example.fitapp.support.TestUsers.Session;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExerciseIntegrationTest extends AbstractIntegrationTest {

    private static Map<String, Object> custom(String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("instructions", "Do it carefully");
        m.put("primaryMuscle", "BICEPS");
        m.put("secondaryMuscles", List.of("FOREARMS"));
        m.put("equipment", "DUMBBELL");
        m.put("jointTags", List.of("ELBOW"));
        m.put("compound", false);
        return m;
    }

    @Test
    void libraryIsSeededWithAtLeastEightyExercises() throws Exception {
        Session s = users().register("Lifter");
        JsonNode page = body(get(s, "/api/exercises?size=100"));
        assertThat(page.get("total").asLong()).isGreaterThanOrEqualTo(80);
        JsonNode first = page.get("items").get(0);
        assertThat(first.get("instructions").asText()).isNotBlank();
        assertThat(first.get("custom").asBoolean()).isFalse();
    }

    @Test
    void filtersByMuscleAndEquipment() throws Exception {
        Session s = users().register("Filter");
        JsonNode chest = body(get(s, "/api/exercises?muscle=CHEST&size=100")).get("items");
        assertThat(chest).isNotEmpty();
        chest.forEach(e -> assertThat(e.get("primaryMuscle").asText()).isEqualTo("CHEST"));

        JsonNode dumbbells = body(get(s, "/api/exercises?equipment=DUMBBELL&size=100")).get("items");
        assertThat(dumbbells).isNotEmpty();
        dumbbells.forEach(e -> assertThat(e.get("equipment").asText()).isEqualTo("DUMBBELL"));
    }

    @Test
    void searchIsCaseInsensitiveAndTreatsWildcardsLiterally() throws Exception {
        Session s = users().register("Search");
        JsonNode hits = body(get(s, "/api/exercises?q=BENCH%20press")).get("items");
        assertThat(hits).isNotEmpty();
        hits.forEach(e -> assertThat(e.get("name").asText().toLowerCase()).contains("bench press"));

        // "%" must not match everything
        assertThat(body(get(s, "/api/exercises?q=%25")).get("items")).isEmpty();
    }

    @Test
    void sizeIsCappedAtOneHundred() throws Exception {
        Session s = users().register("Paging");
        JsonNode page = body(get(s, "/api/exercises?size=5000"));
        assertThat(page.get("size").asInt()).isEqualTo(100);

        JsonNode small = body(get(s, "/api/exercises?size=10&page=0"));
        assertThat(small.get("items")).hasSize(10);
        assertThat(small.get("hasNext").asBoolean()).isTrue();
    }

    @Test
    void usersCanCreateEditAndDeleteCustomExercises() throws Exception {
        Session s = users().register("Custom");
        JsonNode created = body(post(s, "/api/exercises", custom("Zottman Curl")).andExpect(status().isCreated()));
        String id = created.get("id").asText();
        assertThat(created.get("custom").asBoolean()).isTrue();

        Map<String, Object> edited = custom("Zottman Curl Heavy");
        put(s, "/api/exercises/" + id, edited)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Zottman Curl Heavy"));

        JsonNode mine = body(get(s, "/api/exercises?q=zottman")).get("items");
        assertThat(mine).hasSize(1);

        delete(s, "/api/exercises/" + id).andExpect(status().isNoContent());
        assertThat(body(get(s, "/api/exercises?q=zottman")).get("items")).isEmpty();
        get(s, "/api/exercises/" + id).andExpect(status().isNotFound());
    }

    @Test
    void creatingWithAClientIdIsIdempotent() throws Exception {
        Session s = users().register("Retry");
        UUID id = UUID.randomUUID();
        Map<String, Object> req = custom("Retried Curl");
        req.put("id", id.toString());

        post(s, "/api/exercises", req).andExpect(status().isCreated());
        post(s, "/api/exercises", req).andExpect(status().isCreated());

        assertThat(body(get(s, "/api/exercises?q=Retried%20Curl")).get("items")).hasSize(1);
    }

    @Test
    void builtInExercisesCannotBeChanged() throws Exception {
        Session s = users().register("Builtin");
        UUID bench = exerciseId(s, "Barbell Bench Press");
        put(s, "/api/exercises/" + bench, custom("Hacked"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        delete(s, "/api/exercises/" + bench).andExpect(status().isForbidden());
    }

    @Test
    void customExercisesArePrivateToTheirOwner() throws Exception {
        Session alice = users().register("Alice");
        Session bob = users().register("Bob");
        String id = body(post(alice, "/api/exercises", custom("Alice Secret Curl"))).get("id").asText();

        get(bob, "/api/exercises/" + id).andExpect(status().isNotFound());
        put(bob, "/api/exercises/" + id, custom("Stolen")).andExpect(status().isNotFound());
        delete(bob, "/api/exercises/" + id).andExpect(status().isNotFound());
        assertThat(body(get(bob, "/api/exercises?q=secret")).get("items")).isEmpty();

        // and Bob cannot squat the id Alice used
        Map<String, Object> clash = custom("Bob Curl");
        clash.put("id", id);
        post(bob, "/api/exercises", clash).andExpect(status().isConflict());
    }

    @Test
    void validationReturnsFieldErrors() throws Exception {
        Session s = users().register("Invalid");
        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("name", "");
        post(s, "/api/exercises", bad)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.primaryMuscle").exists())
                .andExpect(jsonPath("$.errors.equipment").exists());
    }

    @Test
    void exercisesRequireAuthentication() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/exercises"))
                .andExpect(status().isUnauthorized());
    }
}
