package com.example.fitapp.workout;

import com.example.fitapp.auth.CurrentUser;
import com.example.fitapp.workout.dto.RecordDtos.ExerciseRecords;
import com.example.fitapp.workout.dto.RecordDtos.HistoryPoint;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/records")
@Tag(name = "Records")
public class RecordController {

    private final RecordService service;

    public RecordController(RecordService service) {
        this.service = service;
    }

    @GetMapping
    public List<ExerciseRecords> list() {
        return service.list(CurrentUser.id());
    }

    @GetMapping("/{exerciseId}/history")
    public List<HistoryPoint> history(@PathVariable UUID exerciseId) {
        return service.history(CurrentUser.id(), exerciseId);
    }
}
