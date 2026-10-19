package com.example.fitapp.profile;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.time.Instant;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    Optional<Profile> findByUserIdAndDeletedAtIsNull(UUID userId);

    List<Profile> findByUserIdAndSyncedAtGreaterThanEqualOrderBySyncedAtAscIdAsc(
            UUID userId, Instant since, Pageable pageable);
}
