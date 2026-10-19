package com.example.fitapp.workout;

import com.example.fitapp.exercise.ExerciseService;
import com.example.fitapp.workout.PrDetector.Analysis;
import com.example.fitapp.workout.PrDetector.Best;
import com.example.fitapp.workout.PrDetector.ExerciseBests;
import com.example.fitapp.workout.PrDetector.SetPerformance;
import com.example.fitapp.workout.dto.RecordDtos.ExerciseRecords;
import com.example.fitapp.workout.dto.RecordDtos.HistoryPoint;
import com.example.fitapp.workout.dto.RecordDtos.RepMax;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps session_sets.is_pr and personal_records in step with the user's history. Everything here
 * is derived, so it can be recomputed from scratch for any exercises at any time.
 */
@Service
public class RecordService {

    private final SessionSetRepository sets;
    private final PersonalRecordRepository records;
    private final ExerciseService exerciseService;
    private final SessionRepository sessions;

    public RecordService(
            SessionSetRepository sets,
            PersonalRecordRepository records,
            ExerciseService exerciseService,
            SessionRepository sessions) {
        this.sets = sets;
        this.records = records;
        this.exerciseService = exerciseService;
        this.sessions = sessions;
    }

    /** Recomputes PR flags and record rows for these exercises from the user's full history. */
    @Transactional
    public void recalculate(UUID userId, Set<UUID> exerciseIds) {
        if (exerciseIds.isEmpty()) {
            return;
        }
        List<SessionSet> candidates = sets.findPrCandidates(userId, exerciseIds, SetType.WARMUP);
        List<SetPerformance> performances = new ArrayList<>();
        for (SessionSet s : candidates) {
            performances.add(
                    new SetPerformance(
                            s.getId(),
                            s.getExerciseId(),
                            s.getWeightKg(),
                            s.getReps(),
                            s.getCompletedAt(),
                            s.getSetIndex()));
        }
        Analysis analysis = PrDetector.analyze(performances);

        // Flags: set exactly the sets the analysis says, clear any stale ones (edited, flagged, deleted).
        Map<UUID, SessionSet> touched = new HashMap<>();
        candidates.forEach(s -> touched.put(s.getId(), s));
        sets.findMarkedAsPr(userId, exerciseIds).forEach(s -> touched.put(s.getId(), s));
        Set<UUID> changedSessions = new HashSet<>();
        for (SessionSet s : touched.values()) {
            boolean shouldBePr = analysis.prSetIds().contains(s.getId());
            if (s.isPr() != shouldBePr) {
                s.setPr(shouldBePr);
                sets.save(s);
                changedSessions.add(s.getSessionId());
            }
        }
        if (!changedSessions.isEmpty()) {
            // Other sessions PR badges changed: make sure devices pull them.
            sessions.touchSynced(changedSessions, Instant.now());
        }

        syncRecordRows(userId, exerciseIds, analysis);
    }

    @Transactional(readOnly = true)
    public List<ExerciseRecords> list(UUID userId) {
        List<PersonalRecord> all = records.findByUserIdAndDeletedAtIsNull(userId);
        Map<UUID, List<PersonalRecord>> byExercise =
                all.stream().collect(Collectors.groupingBy(PersonalRecord::getExerciseId));
        Map<UUID, String> names = exerciseService.namesFor(byExercise.keySet());

        List<ExerciseRecords> result = new ArrayList<>();
        for (Map.Entry<UUID, List<PersonalRecord>> e : byExercise.entrySet()) {
            PersonalRecord e1rm =
                    e.getValue().stream()
                            .filter(r -> r.getType() == RecordType.E1RM)
                            .findFirst()
                            .orElse(null);
            List<RepMax> repMaxes =
                    e.getValue().stream()
                            .filter(r -> r.getType() == RecordType.REP_MAX)
                            .sorted(Comparator.comparing(PersonalRecord::getReps))
                            .map(r -> new RepMax(r.getReps(), r.getValue(), r.getAchievedAt()))
                            .toList();
            result.add(
                    new ExerciseRecords(
                            e.getKey(),
                            names.getOrDefault(e.getKey(), "Deleted exercise"),
                            e1rm == null ? null : e1rm.getValue(),
                            e1rm == null ? null : e1rm.getAchievedAt(),
                            repMaxes));
        }
        result.sort(Comparator.comparing(ExerciseRecords::exerciseName));
        return result;
    }

    @Transactional(readOnly = true)
    public List<HistoryPoint> history(UUID userId, UUID exerciseId) {
        exerciseService.get(userId, exerciseId); // 404 unless the caller may see this exercise

        List<SessionSet> candidates = sets.findPrCandidates(userId, Set.of(exerciseId), SetType.WARMUP);
        Map<UUID, List<SessionSet>> bySession = new LinkedHashMap<>();
        candidates.stream()
                .sorted(Comparator.comparing(SessionSet::getCompletedAt))
                .forEach(s -> bySession.computeIfAbsent(s.getSessionId(), k -> new ArrayList<>()).add(s));

        List<HistoryPoint> points = new ArrayList<>();
        for (Map.Entry<UUID, List<SessionSet>> entry : bySession.entrySet()) {
            List<SessionSet> inSession = entry.getValue();
            SessionSet bestE1rmSet =
                    inSession.stream()
                            .filter(s -> s.getReps() >= 1)
                            .max(
                                    Comparator.comparingDouble(
                                            s -> PrDetector.epley(s.getWeightKg(), s.getReps())))
                            .orElse(null);
            SessionSet top =
                    inSession.stream()
                            .max(Comparator.comparingDouble(SessionSet::getWeightKg))
                            .orElse(null);
            if (bestE1rmSet == null || top == null) {
                continue;
            }
            points.add(
                    new HistoryPoint(
                            entry.getKey(),
                            inSession.stream()
                                    .map(SessionSet::getCompletedAt)
                                    .filter(Objects::nonNull)
                                    .max(Comparator.naturalOrder())
                                    .orElse(Instant.EPOCH),
                            round2(PrDetector.epley(bestE1rmSet.getWeightKg(), bestE1rmSet.getReps())),
                            top.getWeightKg(),
                            top.getReps(),
                            inSession.stream().anyMatch(SessionSet::isPr)));
        }
        points.sort(Comparator.comparing(HistoryPoint::performedAt));
        return points;
    }

    private void syncRecordRows(UUID userId, Set<UUID> exerciseIds, Analysis analysis) {
        List<PersonalRecord> existing =
                records.findByUserIdAndExerciseIdInAndDeletedAtIsNull(userId, exerciseIds);
        Map<String, PersonalRecord> byKey = new HashMap<>();
        existing.forEach(r -> byKey.put(key(r.getExerciseId(), r.getType(), r.getReps()), r));

        Set<String> wanted = new HashSet<>();
        for (Map.Entry<UUID, ExerciseBests> e : analysis.bests().entrySet()) {
            UUID exerciseId = e.getKey();
            Best e1rm = e.getValue().e1rm();
            upsert(userId, exerciseId, RecordType.E1RM, null, round2(e1rm.value()), e1rm, byKey, wanted);
            for (Map.Entry<Integer, Best> rm : e.getValue().repMax().entrySet()) {
                Best b = rm.getValue();
                upsert(userId, exerciseId, RecordType.REP_MAX, rm.getKey(), b.value(), b, byKey, wanted);
            }
        }
        Instant now = Instant.now();
        for (Map.Entry<String, PersonalRecord> row : byKey.entrySet()) {
            if (!wanted.contains(row.getKey())) {
                row.getValue().setDeletedAt(now);
                records.save(row.getValue());
            }
        }
    }

    private void upsert(
            UUID userId,
            UUID exerciseId,
            RecordType type,
            Integer reps,
            double value,
            Best best,
            Map<String, PersonalRecord> byKey,
            Set<String> wanted) {
        String k = key(exerciseId, type, reps);
        PersonalRecord row = byKey.get(k);
        if (row == null) {
            row = new PersonalRecord(UUID.randomUUID(), userId, exerciseId, type, reps);
        }
        row.update(value, best.at(), best.setId());
        records.save(row);
        wanted.add(k);
    }

    private static String key(UUID exerciseId, RecordType type, Integer reps) {
        return exerciseId + "|" + type + "|" + (reps == null ? 0 : reps);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
