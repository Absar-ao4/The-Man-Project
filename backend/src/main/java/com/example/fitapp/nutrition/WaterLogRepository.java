package com.example.fitapp.nutrition;
import org.springframework.data.domain.Pageable;
import java.time.Instant;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaterLogRepository extends JpaRepository<WaterLog, UUID> {

    /** Includes soft-deleted rows; the caller revives them. */
    Optional<WaterLog> findByUserIdAndDate(UUID userId, LocalDate date);

    List<WaterLog> findByUserIdAndDateBetweenAndDeletedAtIsNull(
            UUID userId, LocalDate from, LocalDate to);

    List<WaterLog> findByUserIdAndSyncedAtGreaterThanEqualOrderBySyncedAtAscIdAsc(
            UUID userId, Instant since, Pageable pageable);
}
