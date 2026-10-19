package com.example.fitapp.workout;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanDayExerciseRepository extends JpaRepository<PlanDayExercise, UUID> {

    List<PlanDayExercise> findByPlanDayIdInOrderByPlanDayIdAscOrderIndexAsc(
            Collection<UUID> planDayIds);
}
