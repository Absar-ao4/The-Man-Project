package com.example.fitapp.reminder;

/** The kinds of local reminder the app can schedule. The phone owns the schedule; the server stores the choices. */
public enum ReminderType {
    WORKOUT,
    WATER,
    MEAL_BREAKFAST,
    MEAL_LUNCH,
    MEAL_DINNER,
    WEIGH_IN,
    DAILY_GOAL,
    STREAK;

    /** Only the water reminder repeats through the day. */
    public boolean repeats() {
        return this == WATER;
    }
}
