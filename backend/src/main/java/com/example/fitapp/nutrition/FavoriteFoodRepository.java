package com.example.fitapp.nutrition;
import org.springframework.data.domain.Pageable;
import java.time.Instant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteFoodRepository extends JpaRepository<FavoriteFood, UUID> {

    List<FavoriteFood> findByUserIdAndDeletedAtIsNullOrderByFoodNameAsc(UUID userId);

    Optional<FavoriteFood> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    /** Includes soft-deleted rows so a removed favorite can be revived instead of colliding. */
    Optional<FavoriteFood> findByUserIdAndFoodRef(UUID userId, String foodRef);

    List<FavoriteFood> findByUserIdAndSyncedAtGreaterThanEqualOrderBySyncedAtAscIdAsc(
            UUID userId, Instant since, Pageable pageable);
}
