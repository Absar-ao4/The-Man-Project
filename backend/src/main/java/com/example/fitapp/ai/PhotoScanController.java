package com.example.fitapp.ai;

import com.example.fitapp.ai.PhotoScanDtos.PhotoScanRequest;
import com.example.fitapp.ai.PhotoScanDtos.PhotoScanResponse;
import com.example.fitapp.auth.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/nutrition/photo-scan")
@Tag(name = "Photo nutrition")
public class PhotoScanController {

    private final PhotoScanService service;

    public PhotoScanController(PhotoScanService service) {
        this.service = service;
    }

    /**
     * Identifies the foods in a meal photo and returns editable items with nutrition per 100 g. Limited per
     * user per day (free and premium limits are set by an admin). The image is never stored.
     */
    @PostMapping
    public PhotoScanResponse scan(@Valid @RequestBody PhotoScanRequest request) {
        return service.scan(CurrentUser.id(), request);
    }
}
