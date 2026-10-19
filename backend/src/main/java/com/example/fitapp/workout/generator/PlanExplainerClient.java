package com.example.fitapp.workout.generator;

import com.example.fitapp.workout.generator.GeneratorTypes.GeneratedPlan;
import java.util.List;

/** Asks an AI model to put a generated plan into plain words. A seam so tests never call the real API. */
public interface PlanExplainerClient {

    /** The explanation and up to three ideas. The ideas are suggestions only: nothing applies them. */
    record Explanation(String explanation, List<String> suggestions) {}

    Explanation explain(GeneratedPlan plan);
}
