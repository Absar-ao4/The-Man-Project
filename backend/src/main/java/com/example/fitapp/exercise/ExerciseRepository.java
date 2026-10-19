package com.example.fitapp.exercise;
import org.springframework.data.domain.Pageable;
import java.time.Instant;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExerciseRepository
        extends JpaRepository<Exercise, UUID>, JpaSpecificationExecutor<Exercise> {

    /**
     * Exercises the user may reference: built-ins plus their own. Soft-deleted ones count too, so
     * history that used an exercise before it was deleted can still be saved and synced.
     */
    @Query(
            "select e from Exercise e where e.id in :ids"
                    + " and (e.userId is null or e.userId = :userId)")
    List<Exercise> findVisibleByIds(
            @Param("userId") UUID userId, @Param("ids") Collection<UUID> ids);

    List<Exercise> findByUserIdAndSyncedAtGreaterThanEqualOrderBySyncedAtAscIdAsc(
            UUID userId, Instant since, Pageable pageable);
}
