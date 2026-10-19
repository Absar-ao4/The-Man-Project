package com.example.fitapp.profile;

import com.example.fitapp.auth.CurrentUser;
import com.example.fitapp.profile.dto.ProfileDto;
import com.example.fitapp.profile.dto.SuggestTargetsRequest;
import com.example.fitapp.profile.dto.SuggestedTargetsDto;
import com.example.fitapp.profile.dto.TargetsDto;
import com.example.fitapp.profile.dto.UpdateProfileRequest;
import com.example.fitapp.profile.dto.UpdateTargetsRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@Tag(name = "Profile")
public class ProfileController {

    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    @GetMapping("/profile")
    public ProfileDto getProfile() {
        return service.getProfile(CurrentUser.id());
    }

    @PutMapping("/profile")
    public ProfileDto updateProfile(@Valid @RequestBody UpdateProfileRequest req) {
        return service.updateProfile(CurrentUser.id(), req);
    }

    @GetMapping("/targets")
    public TargetsDto getTargets() {
        return service.getTargets(CurrentUser.id());
    }

    @PutMapping("/targets")
    public TargetsDto updateTargets(@Valid @RequestBody UpdateTargetsRequest req) {
        return service.updateTargets(CurrentUser.id(), req);
    }

    /** Computes a suggestion without saving it; the client decides whether to PUT it. */
    @PostMapping("/targets/suggest")
    public SuggestedTargetsDto suggestTargets(
            @Valid @RequestBody(required = false) SuggestTargetsRequest req) {
        return service.suggest(
                CurrentUser.id(),
                req != null ? req : new SuggestTargetsRequest(null, null, null, null, null, null));
    }
}
