package com.example.fitapp.gamification;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface XpEventRepository extends JpaRepository<XpEvent, UUID> {

    boolean existsByUserIdAndReasonAndSourceId(UUID userId, XpReason reason, String sourceId);

    @Query("select coalesce(sum(e.amount), 0) from XpEvent e where e.userId = :userId and e.day = :day")
    int sumForDay(@Param("userId") UUID userId, @Param("day") LocalDate day);

    @Query("select coalesce(sum(e.amount), 0) from XpEvent e where e.userId = :userId")
    long sumTotal(@Param("userId") UUID userId);

    long countByUserIdAndReason(UUID userId, XpReason reason);

    /** What one training session has paid so far: its workout, sets and PR events. */
    @Query(
            "select coalesce(sum(e.amount), 0) from XpEvent e where e.userId = :userId"
                    + " and e.reason in (com.example.fitapp.gamification.XpReason.WORKOUT,"
                    + " com.example.fitapp.gamification.XpReason.SETS,"
                    + " com.example.fitapp.gamification.XpReason.PR)"
                    + " and (e.sourceId = :sessionId or e.sourceId like :sessionPrefix)")
    int sumForSession(
            @Param("userId") UUID userId,
            @Param("sessionId") String sessionId,
            @Param("sessionPrefix") String sessionPrefix);

    List<XpEvent> findByUserIdAndDayBetweenOrderByCreatedAtDescIdDesc(
            UUID userId, LocalDate from, LocalDate to, Pageable pageable);
}
