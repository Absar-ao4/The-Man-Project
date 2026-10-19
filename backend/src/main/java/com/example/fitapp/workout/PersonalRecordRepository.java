package com.example.fitapp.workout;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalRecordRepository extends JpaRepository<PersonalRecord, UUID> {

    List<PersonalRecord> findByUserIdAndExerciseIdInAndDeletedAtIsNull(
            UUID userId, Collection<UUID> exerciseIds);

    List<PersonalRecord> findByUserIdAndDeletedAtIsNull(UUID userId);
}
