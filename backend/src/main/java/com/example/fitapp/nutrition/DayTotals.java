package com.example.fitapp.nutrition;

import java.time.LocalDate;

/** Per-day sums of the meal log, produced by a JPQL constructor expression. */
public record DayTotals(
        LocalDate date, Double calories, Double proteinG, Double carbsG, Double fatG, Long items) {}
