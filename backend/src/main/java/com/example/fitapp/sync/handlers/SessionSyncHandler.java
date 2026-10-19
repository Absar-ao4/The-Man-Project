package com.example.fitapp.sync.handlers;

import com.example.fitapp.sync.BaseSyncHandler;
import com.example.fitapp.sync.RowRejected;
import com.example.fitapp.sync.SyncResult;
import com.example.fitapp.sync.SyncRows.SessionRow;
import com.example.fitapp.sync.SyncRows.SetRow;
import com.example.fitapp.workout.SessionMode;
import com.example.fitapp.workout.SessionRepository;
import com.example.fitapp.workout.SessionService;
import com.example.fitapp.workout.WorkoutSession;
import com.example.fitapp.workout.dto.SessionDtos.SessionDto;
import com.example.fitapp.workout.dto.SessionDtos.SessionRequest;
import com.example.fitapp.workout.dto.SessionDtos.SetRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Sessions travel with their sets. PR and flag verdicts are the server's: they are ignored on push
 * and come back on pull, so a device learns about a PR on the next sync.
 */
@Component
public class SessionSyncHandler extends BaseSyncHandler {

    private final SessionRepository sessions;
    private final SessionService service;

    public SessionSyncHandler(
            ObjectMapper mapper, Validator validator, SessionRepository sessions, SessionService service) {
        super(mapper, validator);
        this.sessions = sessions;
        this.service = service;
    }

    @Override
    public String entity() {
        return "workout_sessions";
    }

    @Override
    public RowOutcome push(UUID userId, ObjectNode node) {
        SessionRow r = parse(node, SessionRow.class);
        requireOwner(r.userId(), userId);
        if (r.deletedAt() == null && r.completed() && r.endedAt() == null) {
            throw new RowRejected("a completed session needs endedAt");
        }

        List<SetRequest> sets = new ArrayList<>();
        for (SetRow s : orEmpty(r.sets())) {
            sets.add(
                    new SetRequest(
                            s.id(),
                            s.exerciseId(),
                            s.setIndex(),
                            s.setType(),
                            s.weightKg(),
                            s.reps(),
                            s.rpe(),
                            s.completed(),
                            s.completedAt()));
        }
        SessionRequest req =
                new SessionRequest(
                        r.id(),
                        r.name(),
                        r.startedAt(),
                        r.endedAt(),
                        r.routineId(),
                        r.mode() == null ? SessionMode.NORMAL : r.mode(),
                        r.lockinProgramId(),
                        r.completed(),
                        r.maxCombo(),
                        r.notes(),
                        sets);
        SyncResult result = service.syncUpsert(userId, r.id(), req, r.updatedAt(), r.deletedAt());
        return result == SyncResult.STALE ? stale(r.id()) : accepted(r.id());
    }

    @Override
    public List<PulledRow> pull(UUID userId, Instant since, int limit) {
        List<WorkoutSession> pulled =
                sessions.findByUserIdAndSyncedAtGreaterThanEqualOrderBySyncedAtAscIdAsc(
                        userId, since, PageRequest.of(0, limit));
        List<SessionDto> dtos = service.loadForSync(pulled);
        List<PulledRow> rows = new ArrayList<>();
        for (int i = 0; i < pulled.size(); i++) {
            WorkoutSession s = pulled.get(i);
            SessionDto d = dtos.get(i);
            List<SetRow> sets =
                    d.sets().stream()
                            .map(
                                    x ->
                                            new SetRow(
                                                    x.id(),
                                                    x.exerciseId(),
                                                    x.setIndex(),
                                                    x.setType(),
                                                    x.weightKg(),
                                                    x.reps(),
                                                    x.rpe(),
                                                    x.completed(),
                                                    x.completedAt(),
                                                    x.pr(),
                                                    x.flagged()))
                            .toList();
            rows.add(
                    new PulledRow(
                            s.getSyncedAt(),
                            toNode(
                                    new SessionRow(
                                            s.getId(),
                                            s.getUserId(),
                                            s.getUpdatedAt(),
                                            s.getDeletedAt(),
                                            s.getName(),
                                            s.getStartedAt(),
                                            s.getEndedAt(),
                                            s.getRoutineId(),
                                            s.getMode(),
                                            s.getLockinProgramId(),
                                            s.isCompleted(),
                                            s.getMaxCombo(),
                                            s.getNotes(),
                                            sets))));
        }
        return rows;
    }
}
