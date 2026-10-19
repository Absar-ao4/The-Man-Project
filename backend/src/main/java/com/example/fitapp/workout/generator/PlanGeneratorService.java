package com.example.fitapp.workout.generator;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.config.AnthropicProperties;
import com.example.fitapp.exercise.Exercise;
import com.example.fitapp.exercise.ExerciseRepository;
import com.example.fitapp.workout.generator.GeneratorTypes.GeneratedPlan;
import com.example.fitapp.workout.generator.GeneratorTypes.Option;
import com.example.fitapp.workout.generator.GeneratorTypes.PlanGenerateRequest;
import com.example.fitapp.workout.generator.PlanExplainerClient.Explanation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Generates plans from the questionnaire and, optionally, has an AI model explain one.
 *
 * <p>Generating is free and deterministic. Explaining is a paid call, so it is limited per user per
 * day. The explain endpoint takes the same answers and rebuilds the plan on the server instead of
 * trusting a plan sent by the client, so nothing a client invents can end up in the prompt.
 */
@Service
public class PlanGeneratorService {

    public record ExplainResult(String explanation, List<String> suggestions, int explainsLeftToday) {}

    private static final Logger log = LoggerFactory.getLogger(PlanGeneratorService.class);
    private static final Set<Integer> SESSION_LENGTHS = Set.of(30, 45, 60, 90);

    private final PlanGenerator generator = new PlanGenerator();
    private final ExerciseRepository exercises;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PlanExplainerClient explainer;
    private final AnthropicProperties anthropic;
    private final Clock clock;

    public PlanGeneratorService(
            ExerciseRepository exercises,
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            PlanExplainerClient explainer,
            AnthropicProperties anthropic,
            Clock clock) {
        this.exercises = exercises;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.explainer = explainer;
        this.anthropic = anthropic;
        this.clock = clock;
    }

    public GeneratedPlan generate(UUID userId, PlanGenerateRequest req) {
        GeneratedPlan plan = build(req);
        saveInputs(userId, req);
        return plan;
    }

    public Optional<PlanGenerateRequest> lastInputs(UUID userId) {
        List<String> rows =
                jdbc.query("select answers::text from plan_generator_inputs where user_id = ?", (rs, i) -> rs.getString(1), userId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(rows.get(0), PlanGenerateRequest.class));
        } catch (JsonProcessingException e) {
            return Optional.empty(); // an old shape of the answers: the user simply starts a new questionnaire
        }
    }

    public ExplainResult explain(UUID userId, PlanGenerateRequest req) {
        if (!anthropic.configured()) {
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "AI explanations are not set up on this server");
        }
        GeneratedPlan plan = build(req);
        int limit = anthropic.explainDailyLimitOrDefault();
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        if (!tryConsume(userId, today, limit)) {
            throw new ApiException(ErrorCode.LIMIT_REACHED, "You have used today's AI explanations");
        }
        try {
            Explanation result = explainer.explain(plan);
            return new ExplainResult(result.explanation(), result.suggestions(), Math.max(0, limit - used(userId, today)));
        } catch (RuntimeException e) {
            refund(userId, today); // a failed call must not cost the user one of their daily explanations
            throw e;
        }
    }

    // ------------------------------------------------------------------ internals

    private GeneratedPlan build(PlanGenerateRequest req) {
        if (!SESSION_LENGTHS.contains(req.sessionMinutes())) {
            throw ApiException.validation("sessionMinutes", "must be 30, 45, 60 or 90");
        }
        List<Option> library =
                exercises.findAll().stream()
                        .filter(e -> e.getUserId() == null && e.getDeletedAt() == null)
                        .map(PlanGeneratorService::toOption)
                        .toList();
        return generator.generate(req, library);
    }

    private static Option toOption(Exercise e) {
        Set<String> tags = new HashSet<>(e.getJointTags() == null ? List.of() : e.getJointTags());
        return new Option(e.getId(), e.getName(), e.getPrimaryMuscle(), e.getEquipment(), tags, e.isCompound());
    }

    private void saveInputs(UUID userId, PlanGenerateRequest req) {
        try {
            jdbc.update(
                    "insert into plan_generator_inputs (user_id, answers, updated_at) values (?, ?::jsonb, now())"
                            + " on conflict (user_id) do update set answers = excluded.answers, updated_at = now()",
                    userId,
                    mapper.writeValueAsString(req));
        } catch (JsonProcessingException e) {
            log.warn("Could not store the plan generator answers");
        }
    }

    /** Takes one of today's explanations if any are left. Atomic: two requests can not both take the last one. */
    private boolean tryConsume(UUID userId, LocalDate day, int limit) {
        jdbc.update("insert into ai_explain_usage (user_id, day, count) values (?, ?, 0) on conflict do nothing", userId, day);
        int updated =
                jdbc.update(
                        "update ai_explain_usage set count = count + 1 where user_id = ? and day = ? and count < ?",
                        userId,
                        day,
                        limit);
        return updated == 1;
    }

    private void refund(UUID userId, LocalDate day) {
        jdbc.update("update ai_explain_usage set count = count - 1 where user_id = ? and day = ? and count > 0", userId, day);
    }

    private int used(UUID userId, LocalDate day) {
        Integer count =
                jdbc.queryForObject(
                        "select coalesce(max(count), 0) from ai_explain_usage where user_id = ? and day = ?",
                        Integer.class,
                        userId,
                        day);
        return count == null ? 0 : count;
    }
}
