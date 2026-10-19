package com.example.fitapp.profile;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.profile.dto.ProfileDto;
import com.example.fitapp.profile.dto.SuggestTargetsRequest;
import com.example.fitapp.profile.dto.SuggestedTargetsDto;
import com.example.fitapp.profile.dto.TargetsDto;
import com.example.fitapp.profile.dto.UpdateProfileRequest;
import com.example.fitapp.profile.dto.UpdateTargetsRequest;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Every method takes the user id explicitly; callers pass the id taken from the JWT. */
@Service
public class ProfileService {

    private final ProfileRepository profiles;
    private final DailyTargetRepository targets;

    public ProfileService(ProfileRepository profiles, DailyTargetRepository targets) {
        this.profiles = profiles;
        this.targets = targets;
    }

    @Transactional
    public Profile createInitial(UUID userId, String name) {
        return profiles.save(new Profile(UUID.randomUUID(), userId, name));
    }

    @Transactional(readOnly = true)
    public ProfileDto getProfile(UUID userId) {
        return toDto(load(userId));
    }

    @Transactional
    public ProfileDto updateProfile(UUID userId, UpdateProfileRequest req) {
        Profile p = load(userId);
        p.setName(req.name().trim());
        p.setAge(req.age());
        p.setSex(req.sex());
        p.setHeightCm(req.heightCm());
        p.setWeightKg(req.weightKg());
        if (req.fitnessLevel() != null) {
            p.setFitnessLevel(req.fitnessLevel());
        }
        if (req.activityLevel() != null) {
            p.setActivityLevel(req.activityLevel());
        }
        if (req.goal() != null) {
            p.setGoal(req.goal());
        }
        if (req.timezone() != null) {
            p.setTimezone(validTimezone(req.timezone()));
        }
        if (req.currency() != null) {
            p.setCurrency(validCurrency(req.currency()));
        }
        if (req.units() != null) {
            p.setUnits(req.units());
        }
        return toDto(profiles.saveAndFlush(p));
    }

    @Transactional(readOnly = true)
    public TargetsDto getTargets(UUID userId) {
        Profile p = load(userId);
        LocalDate today = today(p);
        return targets
                .findFirstByUserIdAndDeletedAtIsNullAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        userId, today)
                .map(ProfileService::toDto)
                .orElseThrow(() -> ApiException.notFound("No daily targets set yet"));
    }

    /** The targets in force today, or null when none were set yet. */
    @Transactional(readOnly = true)
    public TargetsDto findCurrentTargets(UUID userId) {
        Profile p = load(userId);
        return targets
                .findFirstByUserIdAndDeletedAtIsNullAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        userId, today(p))
                .map(ProfileService::toDto)
                .orElse(null);
    }

    @Transactional
    public TargetsDto updateTargets(UUID userId, UpdateTargetsRequest req) {
        Profile p = load(userId);
        LocalDate from = req.effectiveFrom() != null ? req.effectiveFrom() : today(p);
        DailyTarget t =
                targets.findByUserIdAndEffectiveFromAndDeletedAtIsNull(userId, from)
                        .orElseGet(() -> new DailyTarget(UUID.randomUUID(), userId, from));
        t.setValues(
                req.calories(), req.proteinG(), req.carbsG(), req.fatG(), req.waterGlasses());
        return toDto(targets.saveAndFlush(t));
    }

    @Transactional(readOnly = true)
    public SuggestedTargetsDto suggest(UUID userId, SuggestTargetsRequest req) {
        Profile p = load(userId);
        Integer age = pick(req.age(), p.getAge());
        Sex sex = pick(req.sex(), p.getSex());
        Double height = pick(req.heightCm(), p.getHeightCm());
        Double weight = pick(req.weightKg(), p.getWeightKg());

        Map<String, String> missing = new LinkedHashMap<>();
        if (age == null) {
            missing.put("age", "is required to suggest targets");
        }
        if (sex == null) {
            missing.put("sex", "is required to suggest targets");
        }
        if (height == null) {
            missing.put("heightCm", "is required to suggest targets");
        }
        if (weight == null) {
            missing.put("weightKg", "is required to suggest targets");
        }
        if (!missing.isEmpty()) {
            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    "Profile is incomplete: " + String.join(", ", missing.keySet()),
                    missing);
        }

        TargetCalculator.Result r =
                TargetCalculator.suggest(
                        new TargetCalculator.Input(
                                age,
                                sex,
                                height,
                                weight,
                                pick(req.activityLevel(), p.getActivityLevel()),
                                pick(req.goal(), p.getGoal())));
        return new SuggestedTargetsDto(
                r.bmr(), r.tdee(), r.calories(), r.proteinG(), r.carbsG(), r.fatG(), r.waterGlasses());
    }

    private Profile load(UUID userId) {
        return profiles
                .findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> ApiException.notFound("Profile not found"));
    }

    private static <T> T pick(T override, T fallback) {
        return override != null ? override : fallback;
    }

    private static LocalDate today(Profile p) {
        return LocalDate.now(ZoneId.of(p.getTimezone()));
    }

    public static String validTimezone(String tz) {
        try {
            return ZoneId.of(tz).getId();
        } catch (DateTimeException e) {
            throw ApiException.validation("timezone", "is not a valid IANA timezone");
        }
    }

    public static String validCurrency(String code) {
        try {
            return Currency.getInstance(code.toUpperCase()).getCurrencyCode();
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("currency", "is not a valid ISO 4217 currency code");
        }
    }

    private static ProfileDto toDto(Profile p) {
        return new ProfileDto(
                p.getId(),
                p.getName(),
                p.getAge(),
                p.getSex(),
                p.getHeightCm(),
                p.getWeightKg(),
                p.getFitnessLevel(),
                p.getActivityLevel(),
                p.getGoal(),
                p.getTimezone(),
                p.getCurrency(),
                p.getUnits(),
                p.isComplete(),
                p.getUpdatedAt());
    }

    private static TargetsDto toDto(DailyTarget t) {
        return new TargetsDto(
                t.getId(),
                t.getCalories(),
                t.getProteinG(),
                t.getCarbsG(),
                t.getFatG(),
                t.getWaterGlasses(),
                t.getEffectiveFrom());
    }
}
