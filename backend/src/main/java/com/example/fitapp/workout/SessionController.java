package com.example.fitapp.workout;

import com.example.fitapp.auth.CurrentUser;
import com.example.fitapp.common.PageDto;
import com.example.fitapp.workout.dto.SessionDtos.SessionDto;
import com.example.fitapp.workout.dto.SessionDtos.SessionRequest;
import com.example.fitapp.workout.dto.SessionDtos.SessionSummary;
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
@RequestMapping("/api/sessions")
@Tag(name = "Sessions")
public class SessionController {

    private final SessionService service;

    public SessionController(SessionService service) {
        this.service = service;
    }

    @GetMapping
    public PageDto<SessionSummary> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageDto.DEFAULT_SIZE) int size) {
        return service.list(CurrentUser.id(), page, size);
    }

    @GetMapping("/{id}")
    public SessionDto get(@PathVariable UUID id) {
        return service.get(CurrentUser.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionDto create(@Valid @RequestBody SessionRequest req) {
        return service.create(CurrentUser.id(), req);
    }

    @PutMapping("/{id}")
    public SessionDto update(@PathVariable UUID id, @Valid @RequestBody SessionRequest req) {
        return service.update(CurrentUser.id(), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(CurrentUser.id(), id);
    }
}
