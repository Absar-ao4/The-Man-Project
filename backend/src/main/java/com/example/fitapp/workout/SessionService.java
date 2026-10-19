package com.example.fitapp.workout;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.common.PageDto;
import com.example.fitapp.exercise.ExerciseService;
import com.example.fitapp.lockin.LockinProgramRepository;
import com.example.fitapp.sync.SyncResult;
import com.example.fitapp.workout.dto.SessionDtos.SessionDto;
import com.example.fitapp.workout.dto.SessionDtos.SessionRequest;
import com.example.fitapp.workout.dto.SessionDtos.SessionSummary;
import com.example.fitapp.workout.dto.SessionDtos.SetDto;
import com.example.fitapp.workout.dto.SessionDtos.SetRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Saves training sessions and keeps PR data in step. Always scoped to the JWT's user. */
@Service
public class SessionService {

    private final SessionRepository sessions;
    private final SessionSetRepository sets;
    private final RoutineRepository routines;
    private final ExerciseService exerciseService;
    private final RecordService recordService;
    private final LockinProgramRepository lockinPrograms;

    public SessionService(
            SessionRepository sessions,
            SessionSetRepository sets,
            RoutineRepository routines,
            ExerciseService exerciseService,
            RecordService recordService,
            LockinProgramRepository lockinPrograms) {
        this.sessions = sessions;
        this.sets = sets;
        this.routines = routines;
        this.exerciseService = exerciseService;
        this.recordService = recordService;
        this.lockinPrograms = lockinPrograms;
    }

    @Transactional(readOnly = true)
    public PageDto<SessionSummary> list(UUID userId, int page, int size) {
        var result =
                sessions.findByUserIdAndDeletedAtIsNull(
                        userId,
                        PageRequest.of(
                                PageDto.clampPage(page),
                                PageDto.clampSize(size),
                                Sort.by(Sort.Direction.DESC, "startedAt").and(Sort.by("id"))));
        List<UUID> ids = result.getContent().stream().map(WorkoutSession::getId).toList();
        Map<UUID, List<SessionSet>> setsBySession =
                ids.isEmpty()
                        ? Map.of()
                        : sets.findBySessionIdInAndDeletedAtIsNullOrderBySetIndexAsc(ids).stream()
                                .collect(Collectors.groupingBy(SessionSet::getSessionId));
        return PageDto.of(
                result, s -> toSummary(s, setsBySession.getOrDefault(s.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public SessionDto get(UUID userId, UUID id) {
        return toDto(require(userId, id));
    }

    @Transactional
    public SessionDto create(UUID userId, SessionRequest req) {
        UUID id = req.id() != null ? req.id() : UUID.randomUUID();
        WorkoutSession existing = sessions.findById(id).orElse(null);
        if (existing != null) {
            if (!userId.equals(existing.getUserId())) {
                throw new ApiException(ErrorCode.CONFLICT, "That id is already in use");
            }
            existing.setDeletedAt(null);
            return save(userId, existing, req, null);
        }
        return save(userId, new WorkoutSession(id, userId), req, null);
    }

    @Transactional
    public SessionDto update(UUID userId, UUID id, SessionRequest req) {
        return save(userId, require(userId, id), req, null);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        WorkoutSession session = require(userId, id);
        Instant now = Instant.now();
        Set<UUID> exerciseIds = new HashSet<>();
        for (SessionSet s : sets.findBySessionIdAndDeletedAtIsNullOrderBySetIndexAsc(id)) {
            s.setDeletedAt(now);
            s.setPr(false);
            sets.save(s);
            exerciseIds.add(s.getExerciseId());
        }
        session.setDeletedAt(now);
        sessions.saveAndFlush(session);
        recordService.recalculate(userId, exerciseIds);
    }

    private SessionDto save(
            UUID userId, WorkoutSession session, SessionRequest req, Instant clientUpdatedAt) {
        validate(userId, req);
        if (clientUpdatedAt != null) {
            session.setClientUpdatedAt(clientUpdatedAt);
        }

        session.setName(req.name() == null ? "" : req.name().trim());
        session.setStartedAt(req.startedAt());
        session.setEndedAt(req.endedAt());
        session.setRoutineId(req.routineId());
        session.setMode(req.mode() == null ? SessionMode.NORMAL : req.mode());
        session.setLockinProgramId(req.lockinProgramId());
        session.setCompleted(req.completed());
        session.setMaxCombo(req.maxCombo());
        session.setNotes(req.notes() == null ? "" : req.notes().trim());
        sessions.saveAndFlush(session);

        Map<UUID, SessionSet> current =
                sets.findBySessionIdAndDeletedAtIsNullOrderBySetIndexAsc(session.getId()).stream()
                        .collect(Collectors.toMap(SessionSet::getId, Function.identity()));
        Set<UUID> affectedExercises = new HashSet<>();
        current.values().forEach(s -> affectedExercises.add(s.getExerciseId()));

        Set<UUID> keep = new HashSet<>();
        Instant fallbackCompletedAt = session.getEndedAt() != null ? session.getEndedAt() : session.getStartedAt();
        for (SetRequest r : req.sets()) {
            UUID setId = r.id() != null ? r.id() : UUID.randomUUID();
            SessionSet set = current.get(setId);
            if (set == null) {
                set = reviveOrCreate(userId, session.getId(), setId);
            }
            set.setExerciseId(r.exerciseId());
            set.setSetIndex(r.setIndex());
            set.setSetType(r.setType() == null ? SetType.NORMAL : r.setType());
            set.setWeightKg(r.weightKg());
            set.setReps(r.reps());
            set.setRpe(r.rpe());
            set.setCompleted(r.completed());
            set.setCompletedAt(
                    r.completed()
                            ? (r.completedAt() != null ? r.completedAt() : fallbackCompletedAt)
                            : null);
            sets.save(set);
            keep.add(setId);
            affectedExercises.add(r.exerciseId());
        }
        Instant now = Instant.now();
        for (Map.Entry<UUID, SessionSet> row : current.entrySet()) {
            if (!keep.contains(row.getKey())) {
                row.getValue().setDeletedAt(now);
                row.getValue().setPr(false);
                sets.save(row.getValue());
            }
        }
        sets.flush();

        recordService.recalculate(userId, affectedExercises);
        // Sets changed, so the session must appear in the next pull even if its own row did not.
        sessions.touchSynced(List.of(session.getId()), Instant.now());
        return toDto(session);
    }

    /** Applies a session pushed by a device. Last write wins on the device edit time. */
    @Transactional
    public SyncResult syncUpsert(
            UUID userId, UUID id, SessionRequest req, Instant clientUpdatedAt, Instant deletedAt) {
        WorkoutSession existing = sessions.findById(id).orElse(null);
        if (existing != null && !userId.equals(existing.getUserId())) {
            throw new ApiException(ErrorCode.CONFLICT, "That id is already in use");
        }
        if (existing != null && existing.getUpdatedAt().isAfter(clientUpdatedAt)) {
            return SyncResult.STALE;
        }
        if (deletedAt != null) {
            if (existing != null && existing.getDeletedAt() == null) {
                existing.setClientUpdatedAt(clientUpdatedAt);
                softDelete(existing, deletedAt);
            }
            return SyncResult.ACCEPTED;
        }
        WorkoutSession session = existing != null ? existing : new WorkoutSession(id, userId);
        session.setDeletedAt(null);
        save(userId, session, req, clientUpdatedAt);
        return SyncResult.ACCEPTED;
    }

    /** Sessions with their live sets, in pull order, for sync. */
    @Transactional(readOnly = true)
    public List<SessionDto> loadForSync(List<WorkoutSession> pulled) {
        if (pulled.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<SessionSet>> setsBySession =
                sets
                        .findBySessionIdInAndDeletedAtIsNullOrderBySetIndexAsc(
                                pulled.stream().map(WorkoutSession::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(SessionSet::getSessionId));
        Map<UUID, String> names =
                exerciseService.namesFor(
                        setsBySession.values().stream()
                                .flatMap(List::stream)
                                .map(SessionSet::getExerciseId)
                                .collect(Collectors.toSet()));
        return pulled.stream()
                .map(s -> toDto(s, setsBySession.getOrDefault(s.getId(), List.of()), names))
                .toList();
    }

    private void softDelete(WorkoutSession session, Instant at) {
        Instant now = Instant.now();
        Set<UUID> exerciseIds = new HashSet<>();
        for (SessionSet s : sets.findBySessionIdAndDeletedAtIsNullOrderBySetIndexAsc(session.getId())) {
            s.setDeletedAt(at);
            s.setPr(false);
            sets.save(s);
            exerciseIds.add(s.getExerciseId());
        }
        session.setDeletedAt(at);
        sessions.saveAndFlush(session);
        recordService.recalculate(session.getUserId(), exerciseIds);
        sessions.touchSynced(List.of(session.getId()), now);
    }

    private void validate(UUID userId, SessionRequest req) {
        if (req.endedAt() != null && req.endedAt().isBefore(req.startedAt())) {
            throw ApiException.validation("endedAt", "must not be before startedAt");
        }
        if (req.completed() && req.endedAt() == null) {
            throw ApiException.validation("endedAt", "is required when the session is completed");
        }
        for (SetRequest s : req.sets()) {
            if (s.completed() && s.reps() < 1) {
                throw ApiException.validation("sets", "a completed set needs at least 1 rep");
            }
        }
        // A routine deleted after the workout was done still counts: the session keeps pointing at it.
        if (req.routineId() != null
                && routines.findById(req.routineId()).filter(r -> userId.equals(r.getUserId())).isEmpty()) {
            throw ApiException.validation("routineId", "Unknown routine");
        }
        if (req.lockinProgramId() != null && !lockinPrograms.existsById(req.lockinProgramId())) {
            throw ApiException.validation("lockinProgramId", "Unknown Lock-in program");
        }
        exerciseService.requireAllVisible(
                userId, req.sets().stream().map(SetRequest::exerciseId).toList());
    }

    private SessionSet reviveOrCreate(UUID userId, UUID sessionId, UUID setId) {
        SessionSet any = sets.findById(setId).orElse(null);
        if (any == null) {
            return new SessionSet(setId, userId, sessionId);
        }
        if (!userId.equals(any.getUserId()) || !sessionId.equals(any.getSessionId())) {
            throw new ApiException(ErrorCode.CONFLICT, "Set id is already in use");
        }
        any.setDeletedAt(null);
        return any;
    }

    private WorkoutSession require(UUID userId, UUID id) {
        return sessions
                .findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> ApiException.notFound("Session not found"));
    }

    private SessionDto toDto(WorkoutSession s) {
        List<SessionSet> setList =
                sets.findBySessionIdAndDeletedAtIsNullOrderBySetIndexAsc(s.getId());
        Map<UUID, String> names =
                exerciseService.namesFor(
                        setList.stream().map(SessionSet::getExerciseId).collect(Collectors.toSet()));
        return toDto(s, setList, names);
    }

    private static SessionDto toDto(
            WorkoutSession s, List<SessionSet> setList, Map<UUID, String> names) {
        List<SetDto> setDtos =
                setList.stream()
                        .map(
                                x ->
                                        new SetDto(
                                                x.getId(),
                                                x.getExerciseId(),
                                                names.getOrDefault(x.getExerciseId(), "Deleted exercise"),
                                                x.getSetIndex(),
                                                x.getSetType(),
                                                x.getWeightKg(),
                                                x.getReps(),
                                                x.getRpe(),
                                                x.isCompleted(),
                                                x.getCompletedAt(),
                                                x.isPr(),
                                                x.isFlagged()))
                        .toList();
        return new SessionDto(
                s.getId(),
                s.getName(),
                s.getStartedAt(),
                s.getEndedAt(),
                duration(s),
                s.getRoutineId(),
                s.getMode(),
                s.getLockinProgramId(),
                s.isCompleted(),
                s.getMaxCombo(),
                s.getNotes(),
                setDtos,
                s.getUpdatedAt());
    }

    private static SessionSummary toSummary(WorkoutSession s, List<SessionSet> setList) {
        List<SessionSet> done =
                setList.stream().filter(x -> x.isCompleted() && x.getSetType() != SetType.WARMUP).toList();
        double volume = done.stream().mapToDouble(x -> x.getWeightKg() * x.getReps()).sum();
        return new SessionSummary(
                s.getId(),
                s.getName(),
                s.getStartedAt(),
                s.getEndedAt(),
                duration(s),
                s.isCompleted(),
                s.getMode(),
                (int) setList.stream().map(SessionSet::getExerciseId).distinct().count(),
                done.size(),
                Math.round(volume * 100.0) / 100.0,
                (int) setList.stream().filter(SessionSet::isPr).count());
    }

    private static Long duration(WorkoutSession s) {
        return s.getEndedAt() == null
                ? null
                : Duration.between(s.getStartedAt(), s.getEndedAt()).toSeconds();
    }
}
