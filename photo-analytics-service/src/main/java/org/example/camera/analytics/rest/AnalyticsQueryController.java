package org.example.camera.analytics.rest;

import org.example.camera.analytics.db.AnalysisResultEntity;
import org.example.camera.analytics.db.AnalysisResultRepository;
import org.example.camera.common.dto.AnalysisResultDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsQueryController {

    private final AnalysisResultRepository results;

    public AnalyticsQueryController(AnalysisResultRepository results) {
        this.results = results;
    }

    @GetMapping("/sessions/{sessionId}/analysis")
    public List<AnalysisResultDto> getSessionAnalysis(@PathVariable String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId is required");
        }

        List<AnalysisResultDto> out = new ArrayList<>();

        for (AnalysisResultEntity e : results.findAll()) {
            if (e == null) continue;
            if (!sessionId.equals(e.getSessionId())) continue;

            AnalysisResultDto dto = new AnalysisResultDto();
            dto.fileKey = e.getFileKey();
            dto.distance = e.getDistanceMeters();
            dto.speed = e.getSpeedKmh();
            dto.confidence = e.getConfidence();
            dto.objectType = e.getObjectType();
            dto.modelVersion = e.getModelVersion();

            out.add(dto);
        }

        if (out.isEmpty()) {
            // чтобы на защите было понятно, что “нет данных”, а не “сломалось”
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "analysis not found for sessionId=" + sessionId);
        }

        return out;
    }
}
