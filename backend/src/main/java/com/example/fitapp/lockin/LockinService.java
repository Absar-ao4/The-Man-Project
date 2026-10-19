package com.example.fitapp.lockin;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.exercise.Exercise;
import com.example.fitapp.exercise.ExerciseRepository;
import com.example.fitapp.lockin.LockinDtos.DayDto;
import com.example.fitapp.lockin.LockinDtos.LineDto;
import com.example.fitapp.lockin.LockinDtos.ProgramDto;
import com.example.fitapp.profile.FitnessLevel;
import com.example.fitapp.profile.Profile;
import com.example.fitapp.profile.ProfileRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serves the seeded Lock-in programs. Whether a program is open to the user depends on their
 * fitness level (hardcore programs need intermediate or above), so the answer is per user.
 */
@Service
public class LockinService {

    private final LockinProgramRepository programs;
    private final ExerciseRepository exercises;
    private final ProfileRepository profiles;

    public LockinService(
            LockinProgramRepository programs, ExerciseRepository exercises, ProfileRepository profiles) {
        this.programs = programs;
        this.exercises = exercises;
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public List<ProgramDto> list(UUID userId) {
        FitnessLevel level = levelOf(userId);
        return programs.findAllByOrderBySortOrderAsc().stream().map(p -> toDto(p, level, false, Map.of())).toList();
    }

    @Transactional(readOnly = true)
    public ProgramDto get(UUID userId, UUID id) {
        LockinProgram program =
                programs.findById(id).orElseThrow(() -> ApiException.notFound("Lock-in program not found"));
        return toDto(program, levelOf(userId), true, builtInExercises());
    }

    /** Used when a workout claims to be part of a program. */
    @Transactional(readOnly = true)
    public boolean exists(UUID id) {
        return programs.existsById(id);
    }

    private ProgramDto toDto(LockinProgram p, FitnessLevel level, boolean withDays, Map<String, Exercise> library) {
        boolean available = level.ordinal() >= p.getMinLevel().ordinal();
        String reason =
                available
                        ? null
                        : "Needs " + p.getMinLevel().name().toLowerCase(Locale.ROOT) + " level or above.";
        List<DayDto> days = new ArrayList<>();
        if (withDays) {
            int index = 0;
            for (LockinProgram.Day day : p.getDays()) {
                List<LineDto> lines = new ArrayList<>();
                for (LockinProgram.Line l : day.exercises()) {
                    Exercise ex = library.get(l.exercise());
                    lines.add(
                            new LineDto(
                                    ex == null ? null : ex.getId(),
                                    l.exercise(),
                                    ex == null ? null : ex.getPrimaryMuscle().name(),
                                    l.sets(),
                                    l.repMin(),
                                    l.repMax(),
                                    l.restSeconds(),
                                    l.rpe(),
                                    l.method(),
                                    l.group(),
                                    l.drops(),
                                    l.cues()));
                }
                days.add(new DayDto(index++, day.name(), day.focus(), day.estimatedMinutes(), lines));
            }
        }
        return new ProgramDto(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getInstructions(),
                p.getMinLevel().name(),
                p.getDurationMinutes(),
                p.getDays().size(),
                available,
                reason,
                days);
    }

    private Map<String, Exercise> builtInExercises() {
        return exercises.findAll().stream()
                .filter(e -> e.getUserId() == null && e.getDeletedAt() == null)
                .collect(Collectors.toMap(Exercise::getName, e -> e, (a, b) -> a));
    }

    private FitnessLevel levelOf(UUID userId) {
        return profiles.findByUserIdAndDeletedAtIsNull(userId).map(Profile::getFitnessLevel).orElse(FitnessLevel.BEGINNER);
    }
}
