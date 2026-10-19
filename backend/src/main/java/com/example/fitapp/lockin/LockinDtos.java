package com.example.fitapp.lockin;

import java.util.List;
import java.util.UUID;

public final class LockinDtos {

    private LockinDtos() {}

    public record LineDto(
            UUID exerciseId,
            String exerciseName,
            String primaryMuscle,
            int sets,
            int repMin,
            int repMax,
            int restSeconds,
            double rpe,
            String method,
            String group,
            int drops,
            List<String> cues) {}

    public record DayDto(
            int dayIndex, String name, String focus, int estimatedMinutes, List<LineDto> exercises) {}

    /**
     * available is whether THIS user may start the program (their fitness level is high enough);
     * lockedReason says why not. days is only filled in on the detail endpoint.
     */
    public record ProgramDto(
            UUID id,
            String name,
            String description,
            String instructions,
            String minLevel,
            int durationMinutes,
            int dayCount,
            boolean available,
            String lockedReason,
            List<DayDto> days) {}
}
