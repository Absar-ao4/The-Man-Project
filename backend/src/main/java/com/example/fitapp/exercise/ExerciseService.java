package com.example.fitapp.exercise;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.common.PageDto;
import com.example.fitapp.exercise.dto.ExerciseDto;
import com.example.fitapp.exercise.dto.ExerciseRequest;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExerciseService {

    private final ExerciseRepository exercises;

    public ExerciseService(ExerciseRepository exercises) {
        this.exercises = exercises;
    }

    @Transactional(readOnly = true)
    public PageDto<ExerciseDto> search(
            UUID userId, Muscle muscle, Equipment equipment, String query, int page, int size) {
        Specification<Exercise> spec =
                (root, q, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    predicates.add(cb.isNull(root.get("deletedAt")));
                    predicates.add(
                            cb.or(
                                    cb.isNull(root.get("userId")),
                                    cb.equal(root.get("userId"), userId)));
                    if (muscle != null) {
                        predicates.add(cb.equal(root.get("primaryMuscle"), muscle));
                    }
                    if (equipment != null) {
                        predicates.add(cb.equal(root.get("equipment"), equipment));
                    }
                    if (query != null && !query.isBlank()) {
                        predicates.add(
                                cb.like(
                                        cb.lower(root.get("name")),
                                        "%" + escapeLike(query.trim().toLowerCase()) + "%",
                                        '\\'));
                    }
                    return cb.and(predicates.toArray(new Predicate[0]));
                };
        var result =
                exercises.findAll(
                        spec,
                        PageRequest.of(
                                PageDto.clampPage(page),
                                PageDto.clampSize(size),
                                Sort.by("name").ascending().and(Sort.by("id"))));
        return PageDto.of(result, ExerciseMapper::toDto);
    }

    @Transactional(readOnly = true)
    public ExerciseDto get(UUID userId, UUID id) {
        return ExerciseMapper.toDto(requireVisible(userId, id));
    }

    @Transactional
    public ExerciseDto create(UUID userId, ExerciseRequest req) {
        UUID id = req.id() != null ? req.id() : UUID.randomUUID();
        Exercise existing = exercises.findById(id).orElse(null);
        if (existing != null) {
            // Same id resubmitted by its owner is an idempotent retry; anyone else's id is taken.
            if (!userId.equals(existing.getUserId())) {
                throw new ApiException(ErrorCode.CONFLICT, "That id is already in use");
            }
            existing.setDeletedAt(null);
            return ExerciseMapper.toDto(exercises.saveAndFlush(apply(existing, req)));
        }
        return ExerciseMapper.toDto(exercises.saveAndFlush(apply(new Exercise(id, userId), req)));
    }

    @Transactional
    public ExerciseDto update(UUID userId, UUID id, ExerciseRequest req) {
        Exercise e = requireOwnCustom(userId, id);
        return ExerciseMapper.toDto(exercises.saveAndFlush(apply(e, req)));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Exercise e = requireOwnCustom(userId, id);
        e.setDeletedAt(Instant.now());
        exercises.saveAndFlush(e);
    }

    /**
     * Resolves exercise ids a user is allowed to reference (built-in or their own, deleted ones included).
     * Throws a validation error naming the first unknown id, so one bad row never silently
     * links to someone else's custom exercise.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Exercise> requireAllVisible(UUID userId, Collection<UUID> ids) {
        Set<UUID> wanted = new HashSet<>(ids);
        if (wanted.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Exercise> found =
                exercises.findVisibleByIds(userId, wanted).stream()
                        .collect(Collectors.toMap(Exercise::getId, Function.identity()));
        for (UUID id : wanted) {
            if (!found.containsKey(id)) {
                throw ApiException.validation("exerciseId", "Unknown exercise " + id);
            }
        }
        return found;
    }

    /**
     * Display names for ids already stored on the caller's own rows. Includes soft-deleted
     * exercises so history never breaks when a custom exercise is removed later.
     */
    @Transactional(readOnly = true)
    public Map<UUID, String> namesFor(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return exercises.findAllById(new HashSet<>(ids)).stream()
                .collect(Collectors.toMap(Exercise::getId, Exercise::getName));
    }

    private Exercise requireVisible(UUID userId, UUID id) {
        return exercises
                .findById(id)
                .filter(e -> e.getDeletedAt() == null)
                .filter(e -> !e.isCustom() || userId.equals(e.getUserId()))
                .orElseThrow(() -> ApiException.notFound("Exercise not found"));
    }

    private Exercise requireOwnCustom(UUID userId, UUID id) {
        Exercise e = requireVisible(userId, id);
        if (!e.isCustom()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Built-in exercises cannot be changed");
        }
        return e;
    }

    private static Exercise apply(Exercise e, ExerciseRequest req) {
        e.setName(req.name().trim());
        e.setInstructions(req.instructions() == null ? "" : req.instructions().trim());
        e.setPrimaryMuscle(req.primaryMuscle());
        e.setSecondaryMuscles(ExerciseMapper.names(req.secondaryMuscles()));
        e.setEquipment(req.equipment());
        e.setJointTags(ExerciseMapper.names(req.jointTags()));
        e.setCompound(req.compound());
        return e;
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
