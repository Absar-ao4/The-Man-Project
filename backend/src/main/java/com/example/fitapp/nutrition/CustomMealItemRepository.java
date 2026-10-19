package com.example.fitapp.nutrition;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomMealItemRepository extends JpaRepository<CustomMealItem, UUID> {

    List<CustomMealItem> findByMealIdAndDeletedAtIsNullOrderByOrderIndexAsc(UUID mealId);

    List<CustomMealItem> findByMealIdInAndDeletedAtIsNullOrderByOrderIndexAsc(
            Collection<UUID> mealIds);
}
