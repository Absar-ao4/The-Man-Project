package com.example.fitapp.workout;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanDayRepository extends JpaRepository<PlanDay, UUID> {

    List<PlanDay> findByPlanIdInOrderByPlanIdAscDayIndexAsc(Collection<UUID> planIds);
}
