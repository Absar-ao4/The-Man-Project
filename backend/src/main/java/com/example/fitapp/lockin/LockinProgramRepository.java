package com.example.fitapp.lockin;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LockinProgramRepository extends JpaRepository<LockinProgram, UUID> {

    List<LockinProgram> findAllByOrderBySortOrderAsc();
}
