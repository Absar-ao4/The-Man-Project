package com.example.fitapp.body;

import com.example.fitapp.body.BodyDtos.BodySummaryDto;
import com.example.fitapp.body.BodyDtos.MeasurementDto;
import com.example.fitapp.body.BodyDtos.MeasurementRequest;
import com.example.fitapp.body.BodyDtos.TrendPointDto;
import com.example.fitapp.body.BodyDtos.WeightDto;
import com.example.fitapp.body.BodyDtos.WeightRequest;
import com.example.fitapp.body.BodyDtos.WeightTrendDto;
import com.example.fitapp.body.BodyMath.Point;
import com.example.fitapp.body.BodyMath.TrendPoint;
import com.example.fitapp.common.ApiException;
import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.profile.Profile;
import com.example.fitapp.profile.ProfileRepository;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Weigh-ins, tape measurements and the body check built from them. Everything is scoped to the caller; another user's
 * row is a plain 404.
 */
@Service
public class BodyService {

    static final int MAX_RANGE_DAYS = 400;
    /** A measurement older than this is not used for ratios any more: the body has moved on. */
    static final int MEASUREMENT_MAX_AGE_DAYS = 120;

    private final WeightEntryRepository weights;
    private final BodyMeasurementRepository measurements;
    private final ProfileRepository profiles;
    private final Clock clock;

    public BodyService(WeightEntryRepository weights, BodyMeasurementRepository measurements, ProfileRepository profiles, Clock clock) {
        this.weights = weights;
        this.measurements = measurements;
        this.profiles = profiles;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ weigh-ins

    @Transactional(readOnly = true)
    public List<WeightDto> listWeights(UUID userId, LocalDate from, LocalDate to) {
        checkRange(from, to);
        return weights.findByUserIdAndDateBetweenAndDeletedAtIsNullOrderByDateAsc(userId, from, to).stream().map(BodyService::toDto).toList();
    }

    /** One weigh-in per day: a second one for the same day replaces the first. */
    @Transactional
    public WeightDto saveWeight(UUID userId, WeightRequest req) {
        checkNotFuture(userId, req.date());
        WeightEntry entry = weights.findByUserIdAndDate(userId, req.date()).orElse(null);
        if (entry == null) {
            UUID id = req.id() != null ? req.id() : UUID.randomUUID();
            if (weights.existsById(id)) {
                throw new ApiException(ErrorCode.CONFLICT, "That id is already in use");
            }
            entry = new WeightEntry(id, userId, req.date());
        } else {
            entry.setDeletedAt(null);
        }
        entry.setWeightKg(req.weightKg());
        return toDto(weights.saveAndFlush(entry));
    }

    @Transactional
    public WeightDto updateWeight(UUID userId, UUID id, double weightKg) {
        WeightEntry entry = weights.findByIdAndUserIdAndDeletedAtIsNull(id, userId).orElseThrow(() -> ApiException.notFound("Weigh-in not found"));
        entry.setWeightKg(weightKg);
        return toDto(weights.saveAndFlush(entry));
    }

    @Transactional
    public void deleteWeight(UUID userId, UUID id) {
        WeightEntry entry = weights.findByIdAndUserIdAndDeletedAtIsNull(id, userId).orElseThrow(() -> ApiException.notFound("Weigh-in not found"));
        entry.setDeletedAt(Instant.now());
        weights.saveAndFlush(entry);
    }

    // ------------------------------------------------------------------ measurements

    @Transactional(readOnly = true)
    public List<MeasurementDto> listMeasurements(UUID userId, LocalDate from, LocalDate to, MeasurementType type) {
        checkRange(from, to);
        return measurements.findByUserIdAndDateBetweenAndDeletedAtIsNullOrderByDateAscTypeAsc(userId, from, to).stream()
                .filter(m -> type == null || m.getType() == type)
                .map(BodyService::toDto)
                .toList();
    }

    /** One value per body part per day: a second one for the same day and part replaces the first. */
    @Transactional
    public MeasurementDto saveMeasurement(UUID userId, MeasurementRequest req) {
        checkNotFuture(userId, req.date());
        BodyMeasurement m = measurements.findByUserIdAndDateAndType(userId, req.date(), req.type()).orElse(null);
        if (m == null) {
            UUID id = req.id() != null ? req.id() : UUID.randomUUID();
            if (measurements.existsById(id)) {
                throw new ApiException(ErrorCode.CONFLICT, "That id is already in use");
            }
            m = new BodyMeasurement(id, userId, req.date(), req.type());
        } else {
            m.setDeletedAt(null);
        }
        m.setValueCm(req.valueCm());
        return toDto(measurements.saveAndFlush(m));
    }

    @Transactional
    public MeasurementDto updateMeasurement(UUID userId, UUID id, double valueCm) {
        BodyMeasurement m = measurements.findByIdAndUserIdAndDeletedAtIsNull(id, userId).orElseThrow(() -> ApiException.notFound("Measurement not found"));
        m.setValueCm(valueCm);
        return toDto(measurements.saveAndFlush(m));
    }

    @Transactional
    public void deleteMeasurement(UUID userId, UUID id) {
        BodyMeasurement m = measurements.findByIdAndUserIdAndDeletedAtIsNull(id, userId).orElseThrow(() -> ApiException.notFound("Measurement not found"));
        m.setDeletedAt(Instant.now());
        measurements.saveAndFlush(m);
    }

    // ------------------------------------------------------------------ the body check

    /** Weigh-ins in the last [period] (week, month, 3months or year) with the trend as of each day. */
    @Transactional(readOnly = true)
    public WeightTrendDto trend(UUID userId, String period) {
        int days = periodDays(period);
        LocalDate today = today(userId);
        LocalDate from = today.minusDays(days);
        List<Point> all = points(userId);
        List<TrendPointDto> points = new ArrayList<>();
        for (TrendPoint t : BodyMath.trend(all)) {
            if (!t.date().isBefore(from) && !t.date().isAfter(today)) {
                points.add(new TrendPointDto(t.date(), t.weightKg(), t.trendKg()));
            }
        }
        return new WeightTrendDto(period.toLowerCase(), from, today, points);
    }

    @Transactional(readOnly = true)
    public BodySummaryDto summary(UUID userId) {
        Profile profile = profiles.findByUserIdAndDeletedAtIsNull(userId).orElse(null);
        Double height = profile == null ? null : profile.getHeightCm();
        LocalDate today = today(userId);
        List<Point> all = points(userId);
        List<String> notes = new ArrayList<>();

        Point latest = all.stream().max((a, b) -> a.date().compareTo(b.date())).orElse(null);
        Double trendNow = latest == null ? null : BodyMath.trendAsOf(all, latest.date()).orElse(null);
        Double weekAgo = latest == null ? null : BodyMath.trendAsOf(all, latest.date().minusDays(BodyMath.TREND_DAYS)).orElse(null);
        Double weekChange = trendNow != null && weekAgo != null ? Math.round((trendNow - weekAgo) * 10.0) / 10.0 : null;

        Double bmi = null;
        String bmiCategory = null;
        if (trendNow == null) {
            notes.add("Log your weight to see your trend and BMI.");
        } else if (height == null) {
            notes.add("Add your height in the profile to see your BMI.");
        } else {
            Optional<Double> value = BodyMath.bmi(trendNow, height);
            if (value.isPresent()) {
                bmi = value.get();
                bmiCategory = BodyMath.bmiCategory(bmi);
            }
        }
        if (bmi != null) {
            notes.add(BodyMath.BMI_NOTE);
        }

        Map<MeasurementType, Double> latestValues = latestMeasurements(userId, today);
        Double waist = latestValues.get(MeasurementType.WAIST);
        Double ratio = BodyMath.waistToHeight(waist, height).orElse(null);
        String ratioCategory = ratio == null ? null : BodyMath.waistToHeightCategory(ratio);
        if (waist == null) {
            notes.add("Measure your waist to see your waist-to-height ratio.");
        }

        Double bodyFat = null;
        if (profile != null) {
            bodyFat =
                    BodyMath.navyBodyFat(profile.getSex(), height, waist, latestValues.get(MeasurementType.NECK), latestValues.get(MeasurementType.HIPS)).orElse(null);
            if (bodyFat == null && waist != null && (height == null || height < 100 || height > 250)) {
                notes.add("Body fat needs your height in the profile (between 100 and 250 cm).");
            } else if (bodyFat == null && waist != null) {
                notes.add(
                        profile.getSex() == com.example.fitapp.profile.Sex.FEMALE
                                ? "Body fat needs your waist, neck and hip measurements."
                                : profile.getSex() == com.example.fitapp.profile.Sex.MALE
                                        ? "Body fat needs your waist and neck measurements."
                                        : "Body fat is estimated for sex set to male or female in the profile.");
            }
        }
        return new BodySummaryDto(
                latest == null ? null : latest.date(),
                latest == null ? null : latest.weightKg(),
                trendNow,
                weekChange,
                height,
                bmi,
                bmiCategory,
                ratio,
                ratioCategory,
                bodyFat,
                notes);
    }

    /** The most recent value of each body part measured in the last 120 days. */
    private Map<MeasurementType, Double> latestMeasurements(UUID userId, LocalDate today) {
        Map<MeasurementType, Double> latest = new EnumMap<>(MeasurementType.class);
        LocalDate oldest = today.minusDays(MEASUREMENT_MAX_AGE_DAYS);
        for (MeasurementType type : List.of(MeasurementType.WAIST, MeasurementType.NECK, MeasurementType.HIPS)) {
            measurements.findByUserIdAndTypeAndDeletedAtIsNullOrderByDateDesc(userId, type, PageRequest.of(0, 1)).stream()
                    .filter(m -> !m.getDate().isBefore(oldest))
                    .findFirst()
                    .ifPresent(m -> latest.put(type, m.getValueCm()));
        }
        return latest;
    }

    // ------------------------------------------------------------------ helpers

    /** All of a user's live weigh-ins, as plain points (also used by progress and achievements). */
    @Transactional(readOnly = true)
    public List<Point> points(UUID userId) {
        return weights.findByUserIdAndDeletedAtIsNullOrderByDateAsc(userId).stream().map(w -> new Point(w.getDate(), w.getWeightKg())).toList();
    }

    public LocalDate today(UUID userId) {
        return LocalDate.now(clock.withZone(zoneOf(userId)));
    }

    private ZoneId zoneOf(UUID userId) {
        String tz = profiles.findByUserIdAndDeletedAtIsNull(userId).map(Profile::getTimezone).orElse("UTC");
        try {
            return ZoneId.of(tz);
        } catch (DateTimeException e) {
            return ZoneId.of("UTC");
        }
    }

    private void checkNotFuture(UUID userId, LocalDate date) {
        // One day of slack: the phone's date can be a day ahead of the server's view of the user's zone.
        if (date.isAfter(today(userId).plusDays(1))) {
            throw ApiException.validation("date", "must not be in the future");
        }
    }

    private static void checkRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw ApiException.validation("to", "must not be before from");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw ApiException.validation("to", "range is limited to " + MAX_RANGE_DAYS + " days");
        }
    }

    static int periodDays(String period) {
        return switch (period == null ? "" : period.toLowerCase()) {
            case "week" -> 7;
            case "month" -> 30;
            case "3months" -> 90;
            case "year" -> 365;
            default -> throw ApiException.validation("period", "must be week, month, 3months or year");
        };
    }

    private static WeightDto toDto(WeightEntry w) {
        return new WeightDto(w.getId(), w.getDate(), w.getWeightKg(), w.getUpdatedAt());
    }

    private static MeasurementDto toDto(BodyMeasurement m) {
        return new MeasurementDto(m.getId(), m.getDate(), m.getType(), m.getValueCm(), m.getUpdatedAt());
    }
}
