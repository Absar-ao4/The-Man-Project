package com.example.fitapp.workout;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionSetRepository extends JpaRepository<SessionSet, UUID> {

    List<SessionSet> findBySessionIdAndDeletedAtIsNullOrderBySetIndexAsc(UUID sessionId);

    List<SessionSet> findBySessionIdInAndDeletedAtIsNullOrderBySetIndexAsc(
            Collection<UUID> sessionIds);

    /** Completed, unflagged working sets of live sessions: the only sets PR detection may use. */
    @Query(
            "select s from SessionSet s where s.userId = :userId and s.exerciseId in :exerciseIds"
                    + " and s.deletedAt is null and s.completed = true and s.flagged = false"
                    + " and s.setType <> :warmup and s.completedAt is not null"
                    + " and exists (select 1 from WorkoutSession w where w.id = s.sessionId"
                    + " and w.deletedAt is null)")
    List<SessionSet> findPrCandidates(
            @Param("userId") UUID userId,
            @Param("exerciseIds") Collection<UUID> exerciseIds,
            @Param("warmup") SetType warmup);

    @Query(
            "select s from SessionSet s where s.userId = :userId"
                    + " and s.exerciseId in :exerciseIds and s.pr = true")
    List<SessionSet> findMarkedAsPr(
            @Param("userId") UUID userId, @Param("exerciseIds") Collection<UUID> exerciseIds);
}
