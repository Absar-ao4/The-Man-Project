package com.example.fitapp.anticheat;

import com.example.fitapp.anticheat.FlagReviewService.FlagDto;
import com.example.fitapp.auth.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin only (everything under /api/admin needs ROLE_ADMIN). */
@RestController
@RequestMapping("/api/admin/flags")
@Tag(name = "Admin")
public class FlagAdminController {

    private final FlagReviewService service;

    public FlagAdminController(FlagReviewService service) {
        this.service = service;
    }

    /** Flagged sets by status (OPEN by default), oldest first. */
    @GetMapping
    public List<FlagDto> list(@RequestParam(required = false) String status, @RequestParam(defaultValue = "50") int limit) {
        return service.list(status, limit);
    }

    /** The lift was real: clear the flag and pay what was held back. */
    @PostMapping("/{setId}/confirm")
    public FlagDto confirm(@PathVariable UUID setId) {
        return service.confirm(CurrentUser.id(), setId);
    }

    /** The lift was not believable: keep the set flagged for good. */
    @PostMapping("/{setId}/reject")
    public FlagDto reject(@PathVariable UUID setId) {
        return service.reject(CurrentUser.id(), setId);
    }
}
