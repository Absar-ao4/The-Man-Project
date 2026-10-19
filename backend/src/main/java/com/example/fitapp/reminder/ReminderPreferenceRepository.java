package com.example.fitapp.reminder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderPreferenceRepository extends JpaRepository<ReminderPreference, UUID> {

    /** Includes a soft-deleted row; the caller revives it. */
    Optional<ReminderPreference> findByUserIdAndType(UUID userId, ReminderType type);

    List<ReminderPreference> findByUserIdAndDeletedAtIsNullOrderByType(UUID userId);

    List<ReminderPreference> findByUserIdAndSyncedAtGreaterThanEqualOrderBySyncedAtAscIdAsc(UUID userId, Instant since, Pageable pageable);
}
