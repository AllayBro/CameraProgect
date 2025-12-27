package org.example.camera.catalog.rest;

import org.example.camera.common.dto.CatalogImportRequestDto;
import org.example.camera.common.dto.PhotoDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final RestTemplate rest = new RestTemplate();

    @Value("${app.penalty.base-url}")
    private String penaltyBaseUrl;

    @PostMapping("/import")
    public String importFromAnalytics(@RequestBody CatalogImportRequestDto req) {
        int count = (req.manifest != null && req.manifest.photos != null) ? req.manifest.photos.size() : 0;

        for (PhotoDto p : req.manifest.photos) {
            PenaltyCheckRequest r = new PenaltyCheckRequest();
            r.recordId = req.sessionId + ":" + p.fileKey;
            r.speed = 70;
            r.location = p.latitude + "," + p.longitude;
            r.time = p.takenAt;
            r.confidence = 0.9;

            rest.postForEntity(penaltyBaseUrl + "/api/penalty/check", r, Void.class);
        }

        return "IMPORTED photos=" + count;
    }

    public static class PenaltyCheckRequest {
        public String recordId;
        public double speed;
        public String location;
        public String time;
        public double confidence;
    }
}
