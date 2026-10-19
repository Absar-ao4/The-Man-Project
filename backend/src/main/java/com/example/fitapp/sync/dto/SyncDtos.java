package com.example.fitapp.sync.dto;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SyncDtos {

    public static final int MAX_PUSH_ROWS = 200;

    private SyncDtos() {}

    /** One entity per request; rows are that entity's JSON (see SyncRows). */
    public record PushRequest(
            @NotBlank String entity, @NotNull @Size(max = MAX_PUSH_ROWS) List<ObjectNode> rows) {}

    public record RejectedRow(UUID id, String reason) {}

    /** The server kept this device's row under a different id because the day/food already existed. */
    public record IdRemap(UUID from, UUID to) {}

    /** Things to celebrate after a push (XP, badges, level up). Filled from Phase 6 on. */
    public record Celebrations(
            List<Map<String, Object>> xpEvents,
            List<Map<String, Object>> badges,
            Map<String, Object> levelChange) {

        public static Celebrations none() {
            return new Celebrations(List.of(), List.of(), null);
        }
    }

    /**
     * accepted: applied. stale: the server already has a newer edit, adopt it on the next pull.
     * rejected: will never be accepted as sent (validation, ownership), do not retry unchanged.
     */
    public record PushResponse(
            String entity,
            List<UUID> accepted,
            List<UUID> stale,
            List<RejectedRow> rejected,
            List<IdRemap> remaps,
            Instant serverTime,
            Celebrations celebrations) {}

    /**
     * Pass serverTime back as {@code since} on the next pull. hasMore means the page was cut short
     * and another pull should follow immediately.
     */
    public record PullResponse(
            Instant serverTime, boolean hasMore, Map<String, List<ObjectNode>> changes) {}
}
