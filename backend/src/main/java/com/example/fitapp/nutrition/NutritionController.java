package com.example.fitapp.nutrition;

import com.example.fitapp.auth.CurrentUser;
import com.example.fitapp.nutrition.dto.NutritionDtos.NutritionSummary;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/nutrition")
@Tag(name = "Nutrition")
public class NutritionController {

    private final NutritionSummaryService service;

    public NutritionController(NutritionSummaryService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public NutritionSummary summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.summarize(CurrentUser.id(), from, to);
    }
}
