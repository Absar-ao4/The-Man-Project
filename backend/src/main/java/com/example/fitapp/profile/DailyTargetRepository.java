package com.example.fitapp.profile;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.time.Instant;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyTargetRepository extends JpaRepository<DailyTarget, UUID> {

    Optional<DailyTarget> findByUserIdAndEffectiveFromAndDeletedAtIsNull(
            UUID userId, LocalDate effectiveFrom);

    Optional<DailyTarget> findFirstByUserIdAndDeletedAtIsNullAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            UUID userId, LocalDate asOf);

    List<DailyTarget> findByUserIdAndSyncedAtGreaterThanEqualOrderBySyncedAtAscIdAsc(
            UUID userId, Instant since, Pageable pageable);
}
