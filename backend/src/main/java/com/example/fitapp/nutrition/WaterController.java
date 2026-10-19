package com.example.fitapp.nutrition;

import com.example.fitapp.auth.CurrentUser;
import com.example.fitapp.nutrition.dto.NutritionDtos.WaterDto;
import com.example.fitapp.nutrition.dto.NutritionDtos.WaterRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/water")
@Tag(name = "Water")
public class WaterController {

    private final WaterService service;

    public WaterController(WaterService service) {
        this.service = service;
    }

    @GetMapping
    public WaterDto get(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.get(CurrentUser.id(), date);
    }

    @PutMapping
    public WaterDto put(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody WaterRequest req) {
        return service.put(CurrentUser.id(), date, req);
    }
}
