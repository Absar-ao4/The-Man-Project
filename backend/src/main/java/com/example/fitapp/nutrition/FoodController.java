package com.example.fitapp.nutrition;

import com.example.fitapp.auth.CurrentUser;
import com.example.fitapp.nutrition.dto.NutritionDtos.FoodDto;
import com.example.fitapp.nutrition.dto.NutritionDtos.FoodSearchResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/foods")
@Tag(name = "Foods")
public class FoodController {

    private final FoodSearchService service;

    public FoodController(FoodSearchService service) {
        this.service = service;
    }

    /** Your own foods first, then USDA results (or cached results when USDA is unreachable). */
    @GetMapping("/search")
    public FoodSearchResponse search(@RequestParam("q") String query) {
        return service.search(CurrentUser.id(), query);
    }

    @GetMapping("/barcode/{code}")
    public FoodDto barcode(@PathVariable String code) {
        return service.byBarcode(code);
    }
}
