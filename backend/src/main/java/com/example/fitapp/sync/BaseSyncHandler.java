package com.example.fitapp.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Shared row parsing, validation and ownership checks for the entity handlers. */
public abstract class BaseSyncHandler implements SyncEntityHandler {

    protected final ObjectMapper mapper;
    private final Validator validator;

    protected BaseSyncHandler(ObjectMapper mapper, Validator validator) {
        this.mapper = mapper;
        this.validator = validator;
    }

    /** Parses and bean-validates a pushed row; anything wrong rejects the row, not the request. */
    protected <T> T parse(ObjectNode node, Class<T> type) {
        T row;
        try {
            row = mapper.treeToValue(node, type);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new RowRejected("malformed row");
        }
        if (row == null) {
            throw new RowRejected("empty row");
        }
        Set<ConstraintViolation<T>> violations = validator.validate(row);
        if (!violations.isEmpty()) {
            String reason =
                    violations.stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .sorted()
                            .limit(5)
                            .collect(Collectors.joining("; "));
            throw new RowRejected(reason);
        }
        return row;
    }

    /** A row may state its owner, but only if it is the caller. The token is the source of truth. */
    protected static void requireOwner(UUID rowUserId, UUID callerId) {
        if (rowUserId != null && !rowUserId.equals(callerId)) {
            throw new RowRejected("row belongs to another user");
        }
    }

    protected ObjectNode toNode(Object row) {
        return mapper.valueToTree(row);
    }

    protected static boolean isStale(Instant existingUpdatedAt, Instant incomingUpdatedAt) {
        return existingUpdatedAt != null && existingUpdatedAt.isAfter(incomingUpdatedAt);
    }

    protected static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    protected static RowOutcome accepted(UUID id) {
        return new RowOutcome.Accepted(id);
    }

    protected static RowOutcome stale(UUID id) {
        return new RowOutcome.Stale(id);
    }
}
