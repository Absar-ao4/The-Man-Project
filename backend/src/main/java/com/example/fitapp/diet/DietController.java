package com.example.fitapp.diet;

import com.example.fitapp.auth.CurrentUser;
import com.example.fitapp.diet.DietDtos.CheapestProteinListDto;
import com.example.fitapp.diet.DietDtos.FoodDto;
import com.example.fitapp.diet.DietDtos.FoodListDto;
import com.example.fitapp.diet.DietDtos.LogMealRequest;
import com.example.fitapp.diet.DietDtos.LogMealResponse;
import com.example.fitapp.diet.DietDtos.PlanDto;
import com.example.fitapp.diet.DietDtos.PlanRequest;
import com.example.fitapp.diet.DietDtos.PlanSummaryDto;
import com.example.fitapp.diet.DietDtos.PriceRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/diet")
@Tag(name = "Diet planner")
public class DietController {

    private final DietPlanService service;

    public DietController(DietPlanService service) {
        this.service = service;
    }

    /** The price list in your currency (or the one asked for), with your own prices where you set them. */
    @GetMapping("/foods")
    public FoodListDto foods(@RequestParam(required = false) DietType dietType, @RequestParam(required = false) String currency) {
        return service.listFoods(CurrentUser.id(), dietType, currency);
    }

    /** Sets your own price for a food (per its unit, in the food's currency). */
    @PutMapping("/prices/{foodId}")
    public FoodDto setPrice(@PathVariable UUID foodId, @Valid @RequestBody PriceRequest req) {
        return service.setPrice(CurrentUser.id(), foodId, req.price());
    }

    /** Goes back to the default price. */
    @DeleteMapping("/prices/{foodId}")
    public FoodDto clearPrice(@PathVariable UUID foodId) {
        return service.clearPrice(CurrentUser.id(), foodId);
    }

    /** Builds and saves a plan from your latest daily targets. */
    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanDto create(@Valid @RequestBody PlanRequest req) {
        return service.create(CurrentUser.id(), req);
    }

    @GetMapping("/plans")
    public List<PlanSummaryDto> plans() {
        return service.listPlans(CurrentUser.id());
    }

    @GetMapping("/plans/{id}")
    public PlanDto plan(@PathVariable UUID id) {
        return service.getPlan(CurrentUser.id(), id);
    }

    @DeleteMapping("/plans/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.deletePlan(CurrentUser.id(), id);
    }

    /** Adds one planned meal to your food log. */
    @PostMapping("/plans/{id}/log-meal")
    public LogMealResponse logMeal(@PathVariable UUID id, @Valid @RequestBody LogMealRequest req) {
        return service.logMeal(CurrentUser.id(), id, req);
    }

    /** Foods ranked by protein per unit of currency, best first. */
    @GetMapping("/cheapest-protein")
    public CheapestProteinListDto cheapestProtein(@RequestParam(required = false) DietType dietType, @RequestParam(required = false) String currency) {
        return service.cheapestProtein(CurrentUser.id(), dietType, currency);
    }
}
