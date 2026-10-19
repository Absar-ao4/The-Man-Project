package com.example.fitapp.nutrition;

import com.example.fitapp.auth.CurrentUser;
import com.example.fitapp.common.ApiException;
import com.example.fitapp.nutrition.dto.NutritionDtos.MealItemDto;
import com.example.fitapp.nutrition.dto.NutritionDtos.MealItemRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/meals/items")
@Tag(name = "Meals")
public class MealController {

    private final MealService service;

    public MealController(MealService service) {
        this.service = service;
    }

    /** Pass {@code date} for one day, or {@code from} and {@code to} (at most 62 days) for a range. */
    @GetMapping
    public List<MealItemDto> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate start = date != null ? date : from;
        LocalDate end = date != null ? date : to;
        if (start == null || end == null) {
            throw ApiException.validation("date", "pass date, or both from and to");
        }
        return service.list(CurrentUser.id(), start, end);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MealItemDto create(@Valid @RequestBody MealItemRequest req) {
        return service.create(CurrentUser.id(), req);
    }

    @PutMapping("/{id}")
    public MealItemDto update(@PathVariable UUID id, @Valid @RequestBody MealItemRequest req) {
        return service.update(CurrentUser.id(), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(CurrentUser.id(), id);
    }
}
