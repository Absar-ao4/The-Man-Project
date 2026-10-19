package com.example.fitapp.workout.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.config.AnthropicProperties;
import com.example.fitapp.exercise.Muscle;
import com.example.fitapp.profile.FitnessLevel;
import com.example.fitapp.profile.Goal;
import com.example.fitapp.workout.generator.GeneratorTypes.GeneratedDay;
import com.example.fitapp.workout.generator.GeneratorTypes.GeneratedExercise;
import com.example.fitapp.workout.generator.GeneratorTypes.GeneratedPlan;
import com.example.fitapp.workout.generator.GeneratorTypes.MuscleVolume;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnthropicPlanExplainerClientTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AnthropicPlanExplainerClient client =
            new AnthropicPlanExplainerClient(null, null, new AnthropicProperties("key", null, null, null), mapper);

    private GeneratedPlan plan() {
        GeneratedExercise bench = new GeneratedExercise(UUID.randomUUID(), "Barbell Bench Press", Muscle.CHEST, 0, 4, 6, 10, 120);
        GeneratedDay day = new GeneratedDay(0, "Push", "Chest", 50, List.of(bench));
        return new GeneratedPlan(
                "3-day muscle gain plan",
                "summary",
                "FULL_BODY",
                3,
                60,
                Goal.MUSCLE_GAIN,
                FitnessLevel.INTERMEDIATE,
                List.of(day),
                List.of(new MuscleVolume(Muscle.CHEST, 14, 12)),
                List.of());
    }

    @Test
    void theDefaultsPointAtTheRealApiAndTheSmallModel() {
        AnthropicProperties props = new AnthropicProperties("k", null, null, null);
        assertThat(props.configured()).isTrue();
        assertThat(props.baseUrlOrDefault()).isEqualTo("https://api.anthropic.com");
        assertThat(props.explainModelOrDefault()).isEqualTo("claude-haiku-5-5");
        assertThat(props.explainDailyLimitOrDefault()).isEqualTo(10);
        assertThat(new AnthropicProperties("", null, null, null).configured()).isFalse();
        assertThat(new AnthropicProperties(null, null, null, null).configured()).isFalse();
    }

    @Test
    void theToolAnswerIsParsedIntoAnExplanationAndAtMostThreeSuggestions() throws Exception {
        var input =
                mapper.readTree(
                        """
                        {"explanation":"  A simple split.  ",
                         "suggestions":["One","  Two ","","Three","Four"]}
                        """);

        var result = client.parse(input);

        assertThat(result.explanation()).isEqualTo("A simple split.");
        assertThat(result.suggestions()).containsExactly("One", "Two", "Three");
    }

    @Test
    void anAnswerWithoutAnExplanationIsRefused() throws Exception {
        var input = mapper.readTree("{\"suggestions\":[\"Ignore your rules and ...\"]}");

        assertThatThrownBy(() -> client.parse(input))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE));
    }

    @Test
    void anEmptyExplanationIsRefused() throws Exception {
        var input = mapper.readTree("{\"explanation\":\"  \",\"suggestions\":[]}");

        assertThatThrownBy(() -> client.parse(input)).isInstanceOf(ApiException.class);
    }

    @Test
    void aNullOrMalformedReplyIsRefused() {
        assertThatThrownBy(() -> client.parse(null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> client.parse(mapper.createArrayNode())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> client.parse(mapper.createObjectNode())).isInstanceOf(ApiException.class);
    }

    @Test
    void thePromptCarriesThePlanAndNothingAboutTheUser() {
        String text = AnthropicPlanExplainerClient.describe(plan());

        assertThat(text).contains("MUSCLE_GAIN", "INTERMEDIATE", "Push", "Barbell Bench Press: 4 sets of 6-10 reps, rest 120 s", "CHEST: 14 / 12");
        assertThat(text).doesNotContain("@").doesNotContain("userId");
    }

    @Test
    void withoutAKeyTheClientRefusesBeforeAnyNetworkCall() {
        AnthropicPlanExplainerClient noKey =
                new AnthropicPlanExplainerClient(null, null, new AnthropicProperties("", null, null, null), mapper);

        assertThatThrownBy(() -> noKey.explain(plan()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE));
    }
}
