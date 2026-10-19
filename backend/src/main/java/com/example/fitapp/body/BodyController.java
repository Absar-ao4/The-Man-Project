package com.example.fitapp.body;

import com.example.fitapp.auth.CurrentUser;
import com.example.fitapp.body.BodyDtos.BodySummaryDto;
import com.example.fitapp.body.BodyDtos.MeasurementDto;
import com.example.fitapp.body.BodyDtos.MeasurementRequest;
import com.example.fitapp.body.BodyDtos.MeasurementUpdate;
import com.example.fitapp.body.BodyDtos.WeightDto;
import com.example.fitapp.body.BodyDtos.WeightRequest;
import com.example.fitapp.body.BodyDtos.WeightTrendDto;
import com.example.fitapp.body.BodyDtos.WeightUpdate;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Body")
public class BodyController {

    private final BodyService service;

    public BodyController(BodyService service) {
        this.service = service;
    }

    // ------------------------------------------------------------ weigh-ins

    @GetMapping("/weights")
    public List<WeightDto> weights(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.listWeights(CurrentUser.id(), from, to);
    }

    /** Logs a weigh-in. One per day: logging again for the same day replaces it. */
    @PostMapping("/weights")
    @ResponseStatus(HttpStatus.CREATED)
    public WeightDto addWeight(@Valid @RequestBody WeightRequest req) {
        return service.saveWeight(CurrentUser.id(), req);
    }

    @PutMapping("/weights/{id}")
    public WeightDto updateWeight(@PathVariable UUID id, @Valid @RequestBody WeightUpdate req) {
        return service.updateWeight(CurrentUser.id(), id, req.weightKg());
    }

    @DeleteMapping("/weights/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWeight(@PathVariable UUID id) {
        service.deleteWeight(CurrentUser.id(), id);
    }

    // ------------------------------------------------------------ measurements

    @GetMapping("/measurements")
    public List<MeasurementDto> measurements(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) MeasurementType type) {
        return service.listMeasurements(CurrentUser.id(), from, to, type);
    }

    /** Logs a measurement. One per body part per day: logging again replaces it. */
    @PostMapping("/measurements")
    @ResponseStatus(HttpStatus.CREATED)
    public MeasurementDto addMeasurement(@Valid @RequestBody MeasurementRequest req) {
        return service.saveMeasurement(CurrentUser.id(), req);
    }

    @PutMapping("/measurements/{id}")
    public MeasurementDto updateMeasurement(@PathVariable UUID id, @Valid @RequestBody MeasurementUpdate req) {
        return service.updateMeasurement(CurrentUser.id(), id, req.valueCm());
    }

    @DeleteMapping("/measurements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMeasurement(@PathVariable UUID id) {
        service.deleteMeasurement(CurrentUser.id(), id);
    }

    // ------------------------------------------------------------ the body check

    /** Trend weight, BMI (from the trend), waist-to-height and body fat, with notes on what is missing. */
    @GetMapping("/body/summary")
    public BodySummaryDto summary() {
        return service.summary(CurrentUser.id());
    }

    /** Weigh-ins with the 7 day trend as of each day. period: week, month, 3months or year. */
    @GetMapping("/body/trend")
    public WeightTrendDto trend(@RequestParam(defaultValue = "month") String period) {
        return service.trend(CurrentUser.id(), period);
    }
}
