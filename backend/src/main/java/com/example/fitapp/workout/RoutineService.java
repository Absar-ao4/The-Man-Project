package com.example.fitapp.workout;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.exercise.ExerciseService;
import com.example.fitapp.sync.SyncResult;
import com.example.fitapp.workout.dto.RoutineDtos.RoutineDto;
import com.example.fitapp.workout.dto.RoutineDtos.RoutineExerciseDto;
import com.example.fitapp.workout.dto.RoutineDtos.RoutineExerciseRequest;
import com.example.fitapp.workout.dto.RoutineDtos.RoutineRequest;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Every method scopes by the caller's user id; another user's routine simply does not exist. */
@Service
public class RoutineService {

    private final RoutineRepository routines;
    private final RoutineExerciseRepository routineExercises;
    private final ExerciseService exerciseService;

    public RoutineService(
            RoutineRepository routines,
            RoutineExerciseRepository routineExercises,
            ExerciseService exerciseService) {
        this.routines = routines;
        this.routineExercises = routineExercises;
        this.exerciseService = exerciseService;
    }

    @Transactional(readOnly = true)
    public List<RoutineDto> list(UUID userId) {
        List<Routine> all = routines.findByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(userId);
        if (all.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<RoutineExercise>> children =
                routineExercises
                        .findByRoutineIdInAndDeletedAtIsNullOrderByOrderIndexAsc(
                                all.stream().map(Routine::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(RoutineExercise::getRoutineId));
        Map<UUID, String> names =
                exerciseService.namesFor(
                        children.values().stream()
                                .flatMap(List::stream)
                                .map(RoutineExercise::getExerciseId)
                                .toList());
        return all.stream()
                .map(r -> toDto(r, children.getOrDefault(r.getId(), List.of()), names))
                .toList();
    }

    @Transactional(readOnly = true)
    public RoutineDto get(UUID userId, UUID id) {
        return toDto(require(userId, id));
    }

    @Transactional
    public RoutineDto create(UUID userId, RoutineRequest req) {
        UUID id = req.id() != null ? req.id() : UUID.randomUUID();
        Routine existing = routines.findById(id).orElse(null);
        if (existing != null) {
            if (!userId.equals(existing.getUserId())) {
                throw new ApiException(ErrorCode.CONFLICT, "That id is already in use");
            }
            existing.setDeletedAt(null);
            return save(userId, existing, req, null);
        }
        return save(userId, new Routine(id, userId), req, null);
    }

    @Transactional
    public RoutineDto update(UUID userId, UUID id, RoutineRequest req) {
        return save(userId, require(userId, id), req, null);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Routine r = require(userId, id);
        Instant now = Instant.now();
        r.setDeletedAt(now);
        routines.save(r);
        for (RoutineExercise child :
                routineExercises.findByRoutineIdAndDeletedAtIsNullOrderByOrderIndexAsc(id)) {
            child.setDeletedAt(now);
            routineExercises.save(child);
        }
    }

    private RoutineDto save(
            UUID userId, Routine routine, RoutineRequest req, Instant clientUpdatedAt) {
        for (RoutineExerciseRequest e : req.exercises()) {
            if (e.repMin() > e.repMax()) {
                throw ApiException.validation("exercises", "repMin must not exceed repMax");
            }
        }
        exerciseService.requireAllVisible(
                userId, req.exercises().stream().map(RoutineExerciseRequest::exerciseId).toList());

        if (clientUpdatedAt != null) {
            routine.setClientUpdatedAt(clientUpdatedAt);
        }
        routine.setName(req.name().trim());
        routine.setNotes(req.notes() == null ? "" : req.notes().trim());
        routine.setSourcePlanDayId(req.sourcePlanDayId());
        routines.saveAndFlush(routine);

        Map<UUID, RoutineExercise> current =
                routineExercises
                        .findByRoutineIdAndDeletedAtIsNullOrderByOrderIndexAsc(routine.getId())
                        .stream()
                        .collect(Collectors.toMap(RoutineExercise::getId, Function.identity()));
        Set<UUID> keep = new HashSet<>();
        for (RoutineExerciseRequest e : req.exercises()) {
            UUID childId = e.id() != null ? e.id() : UUID.randomUUID();
            RoutineExercise child = current.get(childId);
            if (child == null) {
                child = reviveOrCreate(userId, routine.getId(), childId);
            }
            child.apply(
                    e.exerciseId(),
                    e.orderIndex(),
                    e.targetSets(),
                    e.repMin(),
                    e.repMax(),
                    e.restSeconds());
            routineExercises.save(child);
            keep.add(childId);
        }
        Instant now = Instant.now();
        for (Map.Entry<UUID, RoutineExercise> row : current.entrySet()) {
            if (!keep.contains(row.getKey())) {
                row.getValue().setDeletedAt(now);
                routineExercises.save(row.getValue());
            }
        }
        routineExercises.flush();
        // Children changed, so the aggregate must show up in the next pull even if the parent row did not.
        routines.touchSynced(List.of(routine.getId()), Instant.now());
        return toDto(routine);
    }

    /** Applies a routine pushed by a device. Last write wins on the device's edit time. */
    @Transactional
    public SyncResult syncUpsert(
            UUID userId, UUID id, RoutineRequest req, Instant clientUpdatedAt, Instant deletedAt) {
        Routine existing = routines.findById(id).orElse(null);
        if (existing != null && !userId.equals(existing.getUserId())) {
            throw new ApiException(ErrorCode.CONFLICT, "That id is already in use");
        }
        if (existing != null && existing.getUpdatedAt().isAfter(clientUpdatedAt)) {
            return SyncResult.STALE;
        }
        if (deletedAt != null) {
            if (existing != null) {
                existing.setClientUpdatedAt(clientUpdatedAt);
                existing.setDeletedAt(deletedAt);
                routines.save(existing);
                for (RoutineExercise child :
                        routineExercises.findByRoutineIdAndDeletedAtIsNullOrderByOrderIndexAsc(id)) {
                    child.setDeletedAt(deletedAt);
                    routineExercises.save(child);
                }
                routines.touchSynced(List.of(id), Instant.now());
            }
            return SyncResult.ACCEPTED;
        }
        Routine routine = existing != null ? existing : new Routine(id, userId);
        routine.setDeletedAt(null);
        save(userId, routine, req, clientUpdatedAt);
        return SyncResult.ACCEPTED;
    }

    /** The routine and its live exercises as a client would push them, for pulls. */
    @Transactional(readOnly = true)
    public List<RoutineDto> loadForSync(List<Routine> pulled) {
        if (pulled.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<RoutineExercise>> children =
                routineExercises
                        .findByRoutineIdInAndDeletedAtIsNullOrderByOrderIndexAsc(
                                pulled.stream().map(Routine::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(RoutineExercise::getRoutineId));
        Map<UUID, String> names =
                exerciseService.namesFor(
                        children.values().stream()
                                .flatMap(List::stream)
                                .map(RoutineExercise::getExerciseId)
                                .toList());
        return pulled.stream()
                .map(r -> toDto(r, children.getOrDefault(r.getId(), List.of()), names))
                .toList();
    }

    /** A row id seen before in this routine is revived; one owned elsewhere is a conflict. */
    private RoutineExercise reviveOrCreate(UUID userId, UUID routineId, UUID childId) {
        RoutineExercise any = routineExercises.findById(childId).orElse(null);
        if (any == null) {
            return new RoutineExercise(childId, userId, routineId);
        }
        if (!userId.equals(any.getUserId()) || !routineId.equals(any.getRoutineId())) {
            throw new ApiException(ErrorCode.CONFLICT, "Exercise row id is already in use");
        }
        any.setDeletedAt(null);
        return any;
    }

    private Routine require(UUID userId, UUID id) {
        return routines
                .findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> ApiException.notFound("Routine not found"));
    }

    private RoutineDto toDto(Routine r) {
        List<RoutineExercise> children =
                routineExercises.findByRoutineIdAndDeletedAtIsNullOrderByOrderIndexAsc(r.getId());
        Map<UUID, String> names =
                exerciseService.namesFor(
                        children.stream().map(RoutineExercise::getExerciseId).toList());
        return toDto(r, children, names);
    }

    private static RoutineDto toDto(
            Routine r, List<RoutineExercise> children, Map<UUID, String> names) {
        return new RoutineDto(
                r.getId(),
                r.getName(),
                r.getNotes(),
                r.getSourcePlanDayId(),
                children.stream()
                        .map(
                                c ->
                                        new RoutineExerciseDto(
                                                c.getId(),
                                                c.getExerciseId(),
                                                names.getOrDefault(c.getExerciseId(), "Deleted exercise"),
                                                c.getOrderIndex(),
                                                c.getTargetSets(),
                                                c.getRepMin(),
                                                c.getRepMax(),
                                                c.getRestSeconds()))
                        .toList(),
                r.getUpdatedAt());
    }
}
