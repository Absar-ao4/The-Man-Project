package com.example.fitapp.profile;

/**
 * Pure, deterministic target suggestion: Mifflin-St Jeor BMR x activity factor, then a goal
 * adjustment. Protein is set from body weight, fat at 25% of calories, carbs fill the rest.
 */
public final class TargetCalculator {

    static final double FAT_LOSS_ADJUSTMENT = -0.175; // midpoint of -15% .. -20%
    static final double MUSCLE_GAIN_ADJUSTMENT = 0.125; // midpoint of +10% .. +15%
    static final double FAT_SHARE_OF_CALORIES = 0.25;
    static final int MIN_CALORIES_MALE = 1500;
    static final int MIN_CALORIES_OTHER = 1200;
    static final double ML_PER_KG = 35.0;
    static final double GLASS_ML = 250.0;

    private TargetCalculator() {}

    public record Input(
            int age,
            Sex sex,
            double heightCm,
            double weightKg,
            ActivityLevel activityLevel,
            Goal goal) {}

    public record Result(
            int bmr,
            int tdee,
            int calories,
            int proteinG,
            int carbsG,
            int fatG,
            int waterGlasses) {}

    public static Result suggest(Input in) {
        double bmr = bmr(in.age(), in.sex(), in.heightCm(), in.weightKg());
        double tdee = bmr * in.activityLevel().factor();
        double adjusted = tdee * (1 + adjustment(in.goal()));
        int floor = in.sex() == Sex.MALE ? MIN_CALORIES_MALE : MIN_CALORIES_OTHER;
        int calories = (int) Math.max(floor, Math.round(adjusted));

        int protein = (int) Math.round(in.weightKg() * proteinPerKg(in.goal()));
        int fat = (int) Math.round(calories * FAT_SHARE_OF_CALORIES / 9.0);
        int carbs = (int) Math.max(0, Math.round((calories - protein * 4.0 - fat * 9.0) / 4.0));
        int water = (int) Math.min(16, Math.max(6, Math.ceil(in.weightKg() * ML_PER_KG / GLASS_ML)));

        return new Result(
                (int) Math.round(bmr), (int) Math.round(tdee), calories, protein, carbs, fat, water);
    }

    /** Mifflin-St Jeor. "OTHER" uses the midpoint of the male and female constants. */
    static double bmr(int age, Sex sex, double heightCm, double weightKg) {
        double constant =
                switch (sex) {
                    case MALE -> 5.0;
                    case FEMALE -> -161.0;
                    case OTHER -> -78.0;
                };
        return 10.0 * weightKg + 6.25 * heightCm - 5.0 * age + constant;
    }

    static double adjustment(Goal goal) {
        return switch (goal) {
            case FAT_LOSS -> FAT_LOSS_ADJUSTMENT;
            case MUSCLE_GAIN -> MUSCLE_GAIN_ADJUSTMENT;
            case MAINTENANCE -> 0.0;
        };
    }

    /** All values sit inside the 1.6 to 2.2 g/kg range. */
    static double proteinPerKg(Goal goal) {
        return switch (goal) {
            case FAT_LOSS -> 2.2;
            case MUSCLE_GAIN -> 1.8;
            case MAINTENANCE -> 1.6;
        };
    }
}
