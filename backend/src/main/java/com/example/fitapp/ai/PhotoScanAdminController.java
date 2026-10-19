package com.example.fitapp.ai;

import com.example.fitapp.ai.PhotoScanDtos.PhotoScanConfigDto;
import com.example.fitapp.ai.PhotoScanDtos.PhotoScanStats;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin only: the security configuration requires ROLE_ADMIN for everything under /api/admin. */
@RestController
@RequestMapping("/api/admin/photo-scan")
@Tag(name = "Admin")
public class PhotoScanAdminController {

    private final PhotoScanAdminService service;

    public PhotoScanAdminController(PhotoScanAdminService service) {
        this.service = service;
    }

    @GetMapping("/config")
    public PhotoScanConfigDto config() {
        return service.getConfig();
    }

    @PutMapping("/config")
    public PhotoScanConfigDto update(@Valid @RequestBody PhotoScanConfigDto dto) {
        return service.update(dto);
    }

    @GetMapping("/stats")
    public PhotoScanStats stats() {
        return service.stats();
    }
}
