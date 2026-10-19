package com.example.fitapp.workout;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import java.util.Collection;
import org.springframework.data.domain.Pageable;
import java.time.Instant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineRepository extends JpaRepository<Routine, UUID> {

    List<Routine> findByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(UUID userId);

    Optional<Routine> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    List<Routine> findByUserIdAndSyncedAtGreaterThanEqualOrderBySyncedAtAscIdAsc(
            UUID userId, Instant since, Pageable pageable);

    @Modifying(flushAutomatically = true)
    @Query("update Routine e set e.syncedAt = :now where e.id in :ids")
    void touchSynced(@Param("ids") Collection<UUID> ids, @Param("now") Instant now);
}
