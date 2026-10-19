package com.example.fitapp.nutrition;
import java.time.Instant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomFoodRepository extends JpaRepository<CustomFood, UUID> {

    List<CustomFood> findByUserIdAndDeletedAtIsNullOrderByNameAsc(UUID userId);

    Optional<CustomFood> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    @Query(
            "select c from CustomFood c where c.userId = :userId and c.deletedAt is null"
                    + " and lower(c.name) like :pattern escape '!' order by c.name")
    List<CustomFood> search(
            @Param("userId") UUID userId, @Param("pattern") String pattern, Pageable pageable);

    List<CustomFood> findByUserIdAndSyncedAtGreaterThanEqualOrderBySyncedAtAscIdAsc(
            UUID userId, Instant since, Pageable pageable);
}
