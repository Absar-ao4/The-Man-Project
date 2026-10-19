package com.example.fitapp.workout;

import com.example.fitapp.profile.FitnessLevel;
import com.example.fitapp.workout.dto.PlanDtos.PlanDetail;
import com.example.fitapp.workout.dto.PlanDtos.PlanSummary;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/plans")
@Tag(name = "Plans")
public class PlanController {

    private final PlanService service;

    public PlanController(PlanService service) {
        this.service = service;
    }

    @GetMapping
    public List<PlanSummary> list(
            @RequestParam(required = false) FitnessLevel level,
            @RequestParam(required = false) PlanSplit split) {
        return service.list(level, split);
    }

    @GetMapping("/{id}")
    public PlanDetail get(@PathVariable UUID id) {
        return service.get(id);
    }
}
