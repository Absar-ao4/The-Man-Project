package com.example.fitapp.sync.handlers;

import com.example.fitapp.exercise.Exercise;
import com.example.fitapp.exercise.ExerciseRepository;
import com.example.fitapp.exercise.JointTag;
import com.example.fitapp.exercise.Muscle;
import com.example.fitapp.sync.BaseSyncHandler;
import com.example.fitapp.sync.RowRejected;
import com.example.fitapp.sync.SyncRows.ExerciseRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** The user's own exercises. Built-in exercises are served by GET /api/exercises, not by sync. */
@Component
public class ExerciseSyncHandler extends BaseSyncHandler {

    private final ExerciseRepository exercises;

    public ExerciseSyncHandler(ObjectMapper mapper, Validator validator, ExerciseRepository exercises) {
        super(mapper, validator);
        this.exercises = exercises;
    }

    @Override
    public String entity() {
        return "exercises";
    }

    @Override
    public RowOutcome push(UUID userId, ObjectNode node) {
        ExerciseRow r = parse(node, ExerciseRow.class);
        requireOwner(r.userId(), userId);

        Exercise existing = exercises.findById(r.id()).orElse(null);
        if (existing != null && !userId.equals(existing.getUserId())) {
            throw new RowRejected("id already in use"); // also covers built-in exercises
        }
        if (existing != null && isStale(existing.getUpdatedAt(), r.updatedAt())) {
            return stale(r.id());
        }
        if (r.deletedAt() != null && existing == null) {
            return accepted(r.id());
        }

        Exercise e = existing != null ? existing : new Exercise(r.id(), userId);
        e.setClientUpdatedAt(r.updatedAt());
        e.setDeletedAt(r.deletedAt());
        e.setName(r.name().trim());
        e.setInstructions(r.instructions() == null ? "" : r.instructions().trim());
        e.setPrimaryMuscle(r.primaryMuscle());
        e.setSecondaryMuscles(orEmpty(r.secondaryMuscles()).stream().map(Muscle::name).distinct().toList());
        e.setEquipment(r.equipment());
        e.setJointTags(orEmpty(r.jointTags()).stream().map(JointTag::name).distinct().toList());
        e.setCompound(r.compound());
        exercises.saveAndFlush(e);
        return accepted(r.id());
    }

    @Override
    public List<PulledRow> pull(UUID userId, Instant since, int limit) {
        return exercises
                .findByUserIdAndSyncedAtGreaterThanEqualOrderBySyncedAtAscIdAsc(
                        userId, since, PageRequest.of(0, limit))
                .stream()
                .map(
                        e ->
                                new PulledRow(
                                        e.getSyncedAt(),
                                        toNode(
                                                new ExerciseRow(
                                                        e.getId(),
                                                        e.getUserId(),
                                                        e.getUpdatedAt(),
                                                        e.getDeletedAt(),
                                                        e.getName(),
                                                        e.getInstructions(),
                                                        e.getPrimaryMuscle(),
                                                        e.getSecondaryMuscles().stream().map(Muscle::valueOf).toList(),
                                                        e.getEquipment(),
                                                        e.getJointTags().stream().map(JointTag::valueOf).toList(),
                                                        e.isCompound()))))
                .toList();
    }
}
