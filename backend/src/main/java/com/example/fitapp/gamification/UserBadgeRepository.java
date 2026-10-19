package com.example.fitapp.gamification;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBadgeRepository extends JpaRepository<UserBadge, UUID> {

    List<UserBadge> findByUserIdOrderByEarnedAtAsc(UUID userId);

    boolean existsByUserIdAndBadgeCode(UUID userId, String badgeCode);
}
