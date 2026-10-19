package com.example.fitapp.gamification;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BadgeRepository extends JpaRepository<Badge, String> {

    List<Badge> findAllByOrderBySortOrderAsc();
}
