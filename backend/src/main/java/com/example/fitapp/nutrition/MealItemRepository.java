package com.example.fitapp.nutrition;
import org.springframework.data.domain.Pageable;
import java.time.Instant;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MealItemRepository extends JpaRepository<MealItem, UUID> {

    List<MealItem> findByUserIdAndDateBetweenAndDeletedAtIsNullOrderByDateAscCreatedAtAsc(
            UUID userId, LocalDate from, LocalDate to);

    Optional<MealItem> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    @Query(
            "select new com.example.fitapp.nutrition.DayTotals(m.date, sum(m.calories),"
                    + " sum(m.proteinG), sum(m.carbsG), sum(m.fatG), count(m))"
                    + " from MealItem m where m.userId = :userId and m.deletedAt is null"
                    + " and m.date between :from and :to group by m.date order by m.date")
    List<DayTotals> totalsByDay(
            @Param("userId") UUID userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    List<MealItem> findByUserIdAndSyncedAtGreaterThanEqualOrderBySyncedAtAscIdAsc(
            UUID userId, Instant since, Pageable pageable);
}
