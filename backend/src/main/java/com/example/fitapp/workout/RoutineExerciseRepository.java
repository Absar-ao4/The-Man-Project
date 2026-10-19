package com.example.fitapp.workout;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineExerciseRepository extends JpaRepository<RoutineExercise, UUID> {

    List<RoutineExercise> findByRoutineIdAndDeletedAtIsNullOrderByOrderIndexAsc(UUID routineId);

    List<RoutineExercise> findByRoutineIdInAndDeletedAtIsNullOrderByOrderIndexAsc(
            Collection<UUID> routineIds);
}
