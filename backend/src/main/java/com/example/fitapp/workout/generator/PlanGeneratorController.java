package com.example.fitapp.workout.generator;

import com.example.fitapp.auth.CurrentUser;
import com.example.fitapp.common.ApiException;
import com.example.fitapp.workout.generator.GeneratorTypes.GeneratedPlan;
import com.example.fitapp.workout.generator.GeneratorTypes.PlanGenerateRequest;
import com.example.fitapp.workout.generator.PlanGeneratorService.ExplainResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/plans/generate")
@Tag(name = "Plan generator")
public class PlanGeneratorController {

    private final PlanGeneratorService service;

    public PlanGeneratorController(PlanGeneratorService service) {
        this.service = service;
    }

    /** Builds a plan from the questionnaire. Nothing is saved except the answers; saving routines is up to the app. */
    @PostMapping
    public GeneratedPlan generate(@Valid @RequestBody PlanGenerateRequest req) {
        return service.generate(CurrentUser.id(), req);
    }

    /** The answers behind this user's latest plan, so the questionnaire can be pre-filled on a new device. */
    @GetMapping("/inputs")
    public PlanGenerateRequest inputs() {
        return service.lastInputs(CurrentUser.id()).orElseThrow(() -> ApiException.notFound("No questionnaire saved yet"));
    }

    /**
     * Optional AI explanation of the plan these answers produce, with two or three tweak ideas. Limited per
     * day. The ideas are suggestions only: they never change the rules-based plan.
     */
    @PostMapping("/explain")
    public ExplainResult explain(@Valid @RequestBody PlanGenerateRequest req) {
        return service.explain(CurrentUser.id(), req);
    }
}
