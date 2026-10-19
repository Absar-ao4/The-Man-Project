package com.example.fitapp.exercise;

import com.example.fitapp.auth.CurrentUser;
import com.example.fitapp.common.PageDto;
import com.example.fitapp.exercise.dto.ExerciseDto;
import com.example.fitapp.exercise.dto.ExerciseRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequestMapping("/api/exercises")
@Tag(name = "Exercises")
public class ExerciseController {

    private final ExerciseService service;

    public ExerciseController(ExerciseService service) {
        this.service = service;
    }

    @GetMapping
    public PageDto<ExerciseDto> search(
            @RequestParam(required = false) Muscle muscle,
            @RequestParam(required = false) Equipment equipment,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageDto.DEFAULT_SIZE) int size) {
        return service.search(CurrentUser.id(), muscle, equipment, query, page, size);
    }

    @GetMapping("/{id}")
    public ExerciseDto get(@PathVariable UUID id) {
        return service.get(CurrentUser.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExerciseDto create(@Valid @RequestBody ExerciseRequest req) {
        return service.create(CurrentUser.id(), req);
    }

    @PutMapping("/{id}")
    public ExerciseDto update(@PathVariable UUID id, @Valid @RequestBody ExerciseRequest req) {
        return service.update(CurrentUser.id(), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(CurrentUser.id(), id);
    }
}
