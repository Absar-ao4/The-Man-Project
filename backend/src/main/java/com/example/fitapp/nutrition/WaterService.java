package com.example.fitapp.nutrition;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.nutrition.dto.NutritionDtos.WaterDto;
import com.example.fitapp.nutrition.dto.NutritionDtos.WaterRequest;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One row per user and day holds the glass count. */
@Service
public class WaterService {

    private final WaterLogRepository logs;

    public WaterService(WaterLogRepository logs) {
        this.logs = logs;
    }

    @Transactional(readOnly = true)
    public WaterDto get(UUID userId, LocalDate date) {
        int glasses =
                logs.findByUserIdAndDate(userId, date)
                        .filter(l -> l.getDeletedAt() == null)
                        .map(WaterLog::getGlasses)
                        .orElse(0);
        return new WaterDto(date, glasses);
    }

    @Transactional
    public WaterDto put(UUID userId, LocalDate date, WaterRequest req) {
        WaterLog log = logs.findByUserIdAndDate(userId, date).orElse(null);
        if (log == null) {
            UUID id = req.id() != null ? req.id() : UUID.randomUUID();
            if (logs.existsById(id)) {
                throw new ApiException(ErrorCode.CONFLICT, "That id is already in use");
            }
            log = new WaterLog(id, userId, date);
        } else {
            log.setDeletedAt(null);
        }
        log.setGlasses(req.glasses());
        logs.saveAndFlush(log);
        return new WaterDto(date, log.getGlasses());
    }
}
