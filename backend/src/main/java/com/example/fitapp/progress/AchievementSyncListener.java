package com.example.fitapp.progress;

import com.example.fitapp.sync.SyncListener;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** After workouts or weigh-ins are synced, records any milestone they completed. A failure here never fails the sync. */
@Component
public class AchievementSyncListener implements SyncListener {

    private final AchievementService achievements;

    public AchievementSyncListener(AchievementService achievements) {
        this.achievements = achievements;
    }

    @Override
    public void onPushed(UUID userId, String entity, List<UUID> acceptedIds, Collector out) {
        if ("workout_sessions".equals(entity) || "weight_entries".equals(entity)) {
            achievements.evaluate(userId);
        }
    }
}
