package com.example.fitapp.recommend;

import com.example.fitapp.auth.CurrentUser;
import com.example.fitapp.recommend.RecommendDtos.MealSuggestionRequest;
import com.example.fitapp.recommend.RecommendDtos.MealSuggestionsDto;
import com.example.fitapp.recommend.RecommendDtos.WorkoutRecommendationsDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI")
public class RecommendController {

    private final MealSuggestionService meals;
    private final WorkoutRecommendationService workouts;

    public RecommendController(MealSuggestionService meals, WorkoutRecommendationService workouts) {
        this.meals = meals;
        this.workouts = workouts;
    }

    /** Meal ideas that fit what is left of today's targets. The body is optional. */
    @PostMapping("/meal-suggestions")
    public MealSuggestionsDto mealSuggestions(@Valid @RequestBody(required = false) MealSuggestionRequest req) {
        return meals.suggest(CurrentUser.id(), req);
    }

    /** Progressive overload advice per exercise, and a look at how balanced the last 7 days of training were. */
    @GetMapping("/workout-recommendations")
    public WorkoutRecommendationsDto workoutRecommendations() {
        return workouts.get(CurrentUser.id());
    }
}
