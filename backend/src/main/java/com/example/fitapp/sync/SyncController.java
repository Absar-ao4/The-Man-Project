package com.example.fitapp.sync;

import com.example.fitapp.auth.CurrentUser;
import com.example.fitapp.sync.dto.SyncDtos.PullResponse;
import com.example.fitapp.sync.dto.SyncDtos.PushRequest;
import com.example.fitapp.sync.dto.SyncDtos.PushResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync")
@Tag(name = "Sync")
public class SyncController {

    private final SyncService service;

    public SyncController(SyncService service) {
        this.service = service;
    }

    /** Last write wins on each row's updatedAt. The user id always comes from the token. */
    @PostMapping("/push")
    public PushResponse push(@Valid @RequestBody PushRequest req) {
        return service.push(CurrentUser.id(), req);
    }

    /** Omit {@code since} (or pass the epoch) for a full restore. Pass the previous serverTime afterwards. */
    @GetMapping("/pull")
    public PullResponse pull(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant since,
            @RequestParam(required = false) Integer limit) {
        return service.pull(CurrentUser.id(), since == null ? Instant.EPOCH : since, limit);
    }
}
