package com.example.fitapp.nutrition;

import com.example.fitapp.auth.CurrentUser;
import com.example.fitapp.nutrition.dto.NutritionDtos.CustomMealDto;
import com.example.fitapp.nutrition.dto.NutritionDtos.CustomMealRequest;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/custom-meals")
@Tag(name = "Custom meals")
public class CustomMealController {

    private final CustomMealService service;

    public CustomMealController(CustomMealService service) {
        this.service = service;
    }

    @GetMapping
    public List<CustomMealDto> list() {
        return service.list(CurrentUser.id());
    }

    @GetMapping("/{id}")
    public CustomMealDto get(@PathVariable UUID id) {
        return service.get(CurrentUser.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomMealDto create(@Valid @RequestBody CustomMealRequest req) {
        return service.create(CurrentUser.id(), req);
    }

    @PutMapping("/{id}")
    public CustomMealDto update(@PathVariable UUID id, @Valid @RequestBody CustomMealRequest req) {
        return service.update(CurrentUser.id(), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(CurrentUser.id(), id);
    }
}
