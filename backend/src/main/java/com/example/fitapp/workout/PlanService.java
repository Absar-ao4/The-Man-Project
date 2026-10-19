package com.example.fitapp.workout;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.exercise.Exercise;
import com.example.fitapp.exercise.ExerciseRepository;
import com.example.fitapp.profile.FitnessLevel;
import com.example.fitapp.workout.dto.PlanDtos.PlanDayDto;
import com.example.fitapp.workout.dto.PlanDtos.PlanDetail;
import com.example.fitapp.workout.dto.PlanDtos.PlanExercise;
import com.example.fitapp.workout.dto.PlanDtos.PlanSummary;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanService {

    private final WorkoutPlanRepository plans;
    private final PlanDayRepository days;
    private final PlanDayExerciseRepository slots;
    private final ExerciseRepository exercises;

    public PlanService(
            WorkoutPlanRepository plans,
            PlanDayRepository days,
            PlanDayExerciseRepository slots,
            ExerciseRepository exercises) {
        this.plans = plans;
        this.days = days;
        this.slots = slots;
        this.exercises = exercises;
    }

    @Transactional(readOnly = true)
    public List<PlanSummary> list(FitnessLevel level, PlanSplit split) {
        List<WorkoutPlan> matching =
                plans.findAll().stream()
                        .filter(p -> level == null || p.getLevel() == level)
                        .filter(p -> split == null || p.getSplit() == split)
                        .sorted(
                                Comparator.comparing(WorkoutPlan::getSplit)
                                        .thenComparing(WorkoutPlan::getLevel)
                                        .thenComparing(WorkoutPlan::getName))
                        .toList();
        Map<UUID, Long> dayCounts =
                days.findByPlanIdInOrderByPlanIdAscDayIndexAsc(ids(matching, WorkoutPlan::getId))
                        .stream()
                        .collect(Collectors.groupingBy(PlanDay::getPlanId, Collectors.counting()));
        return matching.stream()
                .map(
                        p ->
                                new PlanSummary(
                                        p.getId(),
                                        p.getName(),
                                        p.getSplit(),
                                        p.getLevel(),
                                        p.getDescription(),
                                        p.getDaysPerWeek(),
                                        dayCounts.getOrDefault(p.getId(), 0L).intValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PlanDetail get(UUID id) {
        WorkoutPlan plan =
                plans.findById(id).orElseThrow(() -> ApiException.notFound("Plan not found"));
        List<PlanDay> planDays = days.findByPlanIdInOrderByPlanIdAscDayIndexAsc(List.of(id));
        List<PlanDayExercise> planSlots =
                slots.findByPlanDayIdInOrderByPlanDayIdAscOrderIndexAsc(
                        ids(planDays, PlanDay::getId));
        Map<UUID, Exercise> byId =
                exercises
                        .findAllById(
                                planSlots.stream().map(PlanDayExercise::getExerciseId).distinct().toList())
                        .stream()
                        .collect(Collectors.toMap(Exercise::getId, Function.identity()));
        Map<UUID, List<PlanDayExercise>> slotsByDay =
                planSlots.stream().collect(Collectors.groupingBy(PlanDayExercise::getPlanDayId));

        List<PlanDayDto> dayDtos =
                planDays.stream()
                        .map(
                                d ->
                                        new PlanDayDto(
                                                d.getId(),
                                                d.getDayIndex(),
                                                d.getName(),
                                                d.getFocus(),
                                                slotsByDay.getOrDefault(d.getId(), List.of()).stream()
                                                        .map(s -> toExercise(s, byId.get(s.getExerciseId())))
                                                        .toList()))
                        .toList();
        return new PlanDetail(
                plan.getId(),
                plan.getName(),
                plan.getSplit(),
                plan.getLevel(),
                plan.getDescription(),
                plan.getDaysPerWeek(),
                dayDtos);
    }

    private static PlanExercise toExercise(PlanDayExercise s, Exercise e) {
        return new PlanExercise(
                s.getExerciseId(),
                e.getName(),
                e.getPrimaryMuscle(),
                s.getOrderIndex(),
                s.getSets(),
                s.getRepMin(),
                s.getRepMax(),
                s.getRestSeconds());
    }

    private static <T> Collection<UUID> ids(List<T> items, Function<T, UUID> id) {
        // An empty IN list is invalid SQL on some drivers, so use a never-matching placeholder.
        return items.isEmpty() ? List.of(new UUID(0, 0)) : items.stream().map(id).toList();
    }
}
