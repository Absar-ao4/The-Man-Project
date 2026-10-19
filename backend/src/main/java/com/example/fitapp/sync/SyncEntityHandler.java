package com.example.fitapp.sync;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One syncable entity. Later phases add their tables by registering another handler bean; nothing
 * else in the sync pipeline needs to change.
 */
public interface SyncEntityHandler {

    /** Name used in push requests and pull responses, e.g. "routines". */
    String entity();

    /** Server-derived entities are pull only. */
    default boolean pushable() {
        return true;
    }

    /** Applies one row for this user. Throws {@link RowRejected} (or ApiException) if it can never be accepted. */
    RowOutcome push(UUID userId, ObjectNode row);

    /**
     * Rows changed on the server at or after {@code since}, oldest first, at most {@code limit},
     * including soft-deleted ones. The service asks for one extra row to detect truncation.
     */
    List<PulledRow> pull(UUID userId, Instant since, int limit);

    record PulledRow(Instant syncedAt, ObjectNode row) {}

    /** What happened to a pushed row. */
    sealed interface RowOutcome {
        record Accepted(UUID id) implements RowOutcome {}

        record Stale(UUID id) implements RowOutcome {}

        /** The row was applied onto an existing row that has a different id. */
        record Remapped(UUID from, UUID to, boolean applied) implements RowOutcome {}
    }
}
