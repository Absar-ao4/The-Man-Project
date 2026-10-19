package com.example.fitapp.lockin;

import com.example.fitapp.auth.CurrentUser;
import com.example.fitapp.lockin.LockinDtos.ProgramDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lockin")
@Tag(name = "Lock-in")
public class LockinController {

    private final LockinService service;

    public LockinController(LockinService service) {
        this.service = service;
    }

    /** Every program, with whether the caller may start it. Days are only on the detail call. */
    @GetMapping("/programs")
    public List<ProgramDto> programs() {
        return service.list(CurrentUser.id());
    }

    @GetMapping("/programs/{id}")
    public ProgramDto program(@PathVariable UUID id) {
        return service.get(CurrentUser.id(), id);
    }
}
