package com.example.fitapp.gamification;

import com.example.fitapp.auth.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Gamification")
public class GamificationController {

    private final GamificationService service;

    public GamificationController(GamificationService service) {
        this.service = service;
    }

    /** Level, XP bar, streaks and today's XP. Read only: XP is only ever awarded from synced data. */
    @GetMapping("/me/stats")
    public GamificationDtos.StatsDto stats() {
        return service.stats(CurrentUser.id());
    }

    /** The XP ledger, newest first. Defaults to the last 30 days; at most 500 rows. */
    @GetMapping("/me/xp")
    public List<GamificationDtos.XpEventDto> xp(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "200") int limit) {
        return service.xp(CurrentUser.id(), from, to, limit);
    }

    /** Every badge that exists. */
    @GetMapping("/badges")
    public List<GamificationDtos.BadgeDto> badges() {
        return service.allBadges();
    }

    /** The badges this user has earned. */
    @GetMapping("/me/badges")
    public List<GamificationDtos.EarnedBadgeDto> myBadges() {
        return service.myBadges(CurrentUser.id());
    }
}
