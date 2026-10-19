package com.example.fitapp.body;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeightEntryRepository extends JpaRepository<WeightEntry, UUID> {

    /** Includes soft-deleted rows; the caller revives them. */
    Optional<WeightEntry> findByUserIdAndDate(UUID userId, LocalDate date);

    Optional<WeightEntry> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    List<WeightEntry> findByUserIdAndDateBetweenAndDeletedAtIsNullOrderByDateAsc(UUID userId, LocalDate from, LocalDate to);

    List<WeightEntry> findByUserIdAndDeletedAtIsNullOrderByDateAsc(UUID userId);

    List<WeightEntry> findByUserIdAndSyncedAtGreaterThanEqualOrderBySyncedAtAscIdAsc(UUID userId, Instant since, Pageable pageable);
}
