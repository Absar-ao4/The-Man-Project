package com.example.fitapp.progress;

import com.example.fitapp.auth.CurrentUser;
import com.example.fitapp.progress.ProgressDtos.AchievementDto;
import com.example.fitapp.progress.ProgressDtos.ActivityDto;
import com.example.fitapp.progress.ProgressDtos.SummaryDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/progress")
@Tag(name = "Progress")
public class ProgressController {

    private final ProgressService progress;
    private final AchievementService achievements;

    public ProgressController(ProgressService progress, AchievementService achievements) {
        this.progress = progress;
        this.achievements = achievements;
    }

    /** What you did this week or this month so far, next to the previous one. */
    @GetMapping("/summary")
    public SummaryDto summary(@RequestParam(defaultValue = "week") String period) {
        return progress.summary(CurrentUser.id(), period);
    }

    /** Per day workouts, sets and XP for the heatmap and the XP chart (default the last 90 days, at most 365). */
    @GetMapping("/activity")
    public ActivityDto activity(@RequestParam(defaultValue = "90") int days) {
        return progress.activity(CurrentUser.id(), days);
    }

    /** Milestones reached, newest first. They are brought up to date first, so workouts logged through the REST API count too. */
    @GetMapping("/achievements")
    public List<AchievementDto> achievements() {
        achievements.evaluate(CurrentUser.id());
        return achievements.list(CurrentUser.id());
    }
}
