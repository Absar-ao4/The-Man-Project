package com.example.fitapp.body;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BodyMeasurementRepository extends JpaRepository<BodyMeasurement, UUID> {

    /** Includes soft-deleted rows; the caller revives them. */
    Optional<BodyMeasurement> findByUserIdAndDateAndType(UUID userId, LocalDate date, MeasurementType type);

    Optional<BodyMeasurement> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    List<BodyMeasurement> findByUserIdAndDateBetweenAndDeletedAtIsNullOrderByDateAscTypeAsc(UUID userId, LocalDate from, LocalDate to);

    List<BodyMeasurement> findByUserIdAndTypeAndDeletedAtIsNullOrderByDateDesc(UUID userId, MeasurementType type, Pageable pageable);

    List<BodyMeasurement> findByUserIdAndSyncedAtGreaterThanEqualOrderBySyncedAtAscIdAsc(UUID userId, Instant since, Pageable pageable);
}
