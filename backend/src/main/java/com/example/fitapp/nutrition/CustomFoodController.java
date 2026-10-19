package com.example.fitapp.nutrition;

import com.example.fitapp.auth.CurrentUser;
import com.example.fitapp.nutrition.dto.NutritionDtos.CustomFoodDto;
import com.example.fitapp.nutrition.dto.NutritionDtos.CustomFoodRequest;
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
@RequestMapping("/api/custom-foods")
@Tag(name = "Custom foods")
public class CustomFoodController {

    private final CustomFoodService service;

    public CustomFoodController(CustomFoodService service) {
        this.service = service;
    }

    @GetMapping
    public List<CustomFoodDto> list() {
        return service.list(CurrentUser.id());
    }

    @GetMapping("/{id}")
    public CustomFoodDto get(@PathVariable UUID id) {
        return service.get(CurrentUser.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomFoodDto create(@Valid @RequestBody CustomFoodRequest req) {
        return service.create(CurrentUser.id(), req);
    }

    @PutMapping("/{id}")
    public CustomFoodDto update(@PathVariable UUID id, @Valid @RequestBody CustomFoodRequest req) {
        return service.update(CurrentUser.id(), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(CurrentUser.id(), id);
    }
}
