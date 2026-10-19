package com.example.fitapp.nutrition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FoodRepository extends JpaRepository<Food, UUID> {

    Optional<Food> findBySourceAndExternalId(String source, String externalId);

    /** Name search over the local cache. {@code pattern} must be a lower-cased, escaped LIKE pattern. */
    @Query(
            "select f from Food f where lower(f.name) like :pattern escape '!' order by f.name")
    List<Food> searchCached(@Param("pattern") String pattern, Pageable pageable);
}
