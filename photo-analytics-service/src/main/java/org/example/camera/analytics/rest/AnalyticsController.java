package org.example.camera.analytics.rest;

import org.example.camera.analytics.db.CaptureSessionEntity;
import org.example.camera.analytics.service.AnalyticsService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @PostMapping("/sessions")
    public CaptureSessionEntity start(@RequestParam String droneId, @RequestParam String operatorId) {
        return service.startSession(droneId, operatorId);
    }

    @GetMapping("/sessions/{id}")
    public CaptureSessionEntity get(@PathVariable String id) {
        return service.getSession(id);
    }

    @PostMapping(value = "/sessions/{id}/manifest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String uploadManifest(@PathVariable String id, @RequestPart("manifest") MultipartFile manifest) throws Exception {
        int photos = service.submitManifest(id, manifest.getInputStream());
        return "OK, photos=" + photos;
    }
}
