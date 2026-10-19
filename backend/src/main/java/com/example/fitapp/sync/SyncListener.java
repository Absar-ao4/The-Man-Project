package com.example.fitapp.sync;

import com.example.fitapp.sync.dto.SyncDtos.Celebrations;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hook run after rows of an entity were accepted. XP, streaks, badges, anti-cheat flags and feed
 * events are implemented as listeners (Phases 6 and 11), so the sync endpoint stays generic.
 */
public interface SyncListener {

    void onPushed(UUID userId, String entity, List<UUID> acceptedIds, Collector out);

    /** Collects what the app should celebrate in the push response. */
    final class Collector {
        private final java.util.List<Map<String, Object>> xpEvents = new java.util.ArrayList<>();
        private final java.util.List<Map<String, Object>> badges = new java.util.ArrayList<>();
        private Map<String, Object> levelChange;

        public void xpEvent(Map<String, Object> event) {
            xpEvents.add(event);
        }

        public void badge(Map<String, Object> badge) {
            badges.add(badge);
        }

        public void levelChange(Map<String, Object> change) {
            this.levelChange = change;
        }

        public Celebrations toCelebrations() {
            return new Celebrations(List.copyOf(xpEvents), List.copyOf(badges), levelChange);
        }
    }
}
