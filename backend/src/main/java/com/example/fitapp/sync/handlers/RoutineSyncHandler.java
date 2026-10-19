package com.example.fitapp.sync.handlers;

import com.example.fitapp.sync.BaseSyncHandler;
import com.example.fitapp.sync.SyncResult;
import com.example.fitapp.sync.SyncRows.RoutineExerciseRow;
import com.example.fitapp.sync.SyncRows.RoutineRow;
import com.example.fitapp.workout.Routine;
import com.example.fitapp.workout.RoutineRepository;
import com.example.fitapp.workout.RoutineService;
import com.example.fitapp.workout.dto.RoutineDtos.RoutineDto;
import com.example.fitapp.workout.dto.RoutineDtos.RoutineExerciseRequest;
import com.example.fitapp.workout.dto.RoutineDtos.RoutineRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** Routines travel with their exercises as one unit. */
@Component
public class RoutineSyncHandler extends BaseSyncHandler {

    private final RoutineRepository routines;
    private final RoutineService service;

    public RoutineSyncHandler(
            ObjectMapper mapper, Validator validator, RoutineRepository routines, RoutineService service) {
        super(mapper, validator);
        this.routines = routines;
        this.service = service;
    }

    @Override
    public String entity() {
        return "routines";
    }

    @Override
    public RowOutcome push(UUID userId, ObjectNode node) {
        RoutineRow r = parse(node, RoutineRow.class);
        requireOwner(r.userId(), userId);

        List<RoutineExerciseRequest> children = new ArrayList<>();
        for (RoutineExerciseRow c : orEmpty(r.exercises())) {
            children.add(
                    new RoutineExerciseRequest(
                            c.id(), c.exerciseId(), c.orderIndex(), c.targetSets(), c.repMin(), c.repMax(), c.restSeconds()));
        }
        RoutineRequest req =
                new RoutineRequest(r.id(), r.name(), r.notes(), r.sourcePlanDayId(), children);
        SyncResult result = service.syncUpsert(userId, r.id(), req, r.updatedAt(), r.deletedAt());
        return result == SyncResult.STALE ? stale(r.id()) : accepted(r.id());
    }

    @Override
    public List<PulledRow> pull(UUID userId, Instant since, int limit) {
        List<Routine> pulled =
                routines.findByUserIdAndSyncedAtGreaterThanEqualOrderBySyncedAtAscIdAsc(
                        userId, since, PageRequest.of(0, limit));
        List<RoutineDto> dtos = service.loadForSync(pulled);
        List<PulledRow> rows = new ArrayList<>();
        for (int i = 0; i < pulled.size(); i++) {
            Routine r = pulled.get(i);
            RoutineDto d = dtos.get(i);
            List<RoutineExerciseRow> exercises =
                    d.exercises().stream()
                            .map(
                                    e ->
                                            new RoutineExerciseRow(
                                                    e.id(),
                                                    e.exerciseId(),
                                                    e.orderIndex(),
                                                    e.targetSets(),
                                                    e.repMin(),
                                                    e.repMax(),
                                                    e.restSeconds()))
                            .toList();
            rows.add(
                    new PulledRow(
                            r.getSyncedAt(),
                            toNode(
                                    new RoutineRow(
                                            r.getId(),
                                            r.getUserId(),
                                            r.getUpdatedAt(),
                                            r.getDeletedAt(),
                                            r.getName(),
                                            r.getNotes(),
                                            r.getSourcePlanDayId(),
                                            exercises))));
        }
        return rows;
    }
}
