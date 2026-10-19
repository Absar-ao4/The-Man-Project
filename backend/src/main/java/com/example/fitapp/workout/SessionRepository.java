package com.example.fitapp.workout;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import java.util.Collection;
import java.util.List;
import java.time.Instant;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<WorkoutSession, UUID> {

    Optional<WorkoutSession> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    Page<WorkoutSession> findByUserIdAndDeletedAtIsNull(UUID userId, Pageable pageable);

    List<WorkoutSession> findByUserIdAndSyncedAtGreaterThanEqualOrderBySyncedAtAscIdAsc(
            UUID userId, Instant since, Pageable pageable);

    /** When each finished, undeleted workout ended: the days the user trained, for streaks. */
    @Query(
            "select s.endedAt from WorkoutSession s where s.userId = :userId and s.completed = true"
                    + " and s.deletedAt is null and s.endedAt is not null")
    List<Instant> findCompletedEndTimes(@Param("userId") UUID userId);

    @Modifying(flushAutomatically = true)
    @Query("update WorkoutSession e set e.syncedAt = :now where e.id in :ids")
    void touchSynced(@Param("ids") Collection<UUID> ids, @Param("now") Instant now);
}
