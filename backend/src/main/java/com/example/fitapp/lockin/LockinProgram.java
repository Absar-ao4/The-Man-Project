package com.example.fitapp.lockin;

import com.example.fitapp.profile.FitnessLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A seeded Lock-in program. Read only: nothing in the API creates or edits one. */
@Entity
@Table(name = "lockin_programs")
public class LockinProgram {

    /** One exercise line of a day, as stored in the {@code days} JSON. */
    public record Line(
            String exercise,
            int sets,
            int repMin,
            int repMax,
            int restSeconds,
            double rpe,
            String method,
            String group,
            int drops,
            List<String> cues) {}

    public record Day(String name, String focus, int estimatedMinutes, List<Line> exercises) {}

    @Id private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(name = "min_level", nullable = false)
    private FitnessLevel minLevel;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Day> days;

    protected LockinProgram() {}

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getInstructions() {
        return instructions;
    }

    public FitnessLevel getMinLevel() {
        return minLevel;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public List<Day> getDays() {
        return days;
    }
}
