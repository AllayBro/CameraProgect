package org.example.camera.analytics.service;

import org.example.camera.analytics.db.CaptureSessionEntity;
import org.example.camera.analytics.db.CaptureSessionRepository;
import org.example.camera.analytics.xml.ManifestStaxParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

@Service
public class AnalyticsService {

    private final CaptureSessionRepository sessions;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ManifestStaxParser parser = new ManifestStaxParser();

    @Value("${app.catalog.base-url}")
    private String catalogBaseUrl;

    public AnalyticsService(CaptureSessionRepository sessions) {
        this.sessions = sessions;
    }

    public CaptureSessionEntity startSession(String droneId, String operatorId) {
        CaptureSessionEntity s = new CaptureSessionEntity();
        s.setSessionId(UUID.randomUUID().toString());
        s.setDroneId(droneId);
        s.setOperatorId(operatorId);
        s.setStartTime(Instant.now());
        s.setStatus("CREATED");
        return sessions.save(s);
    }

    public CaptureSessionEntity getSession(String sessionId) {
        return sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
    }

    public int submitManifest(String sessionId, InputStream manifestXml) {
        CaptureSessionEntity s = getSession(sessionId);

        ManifestStaxParser.Manifest m = parser.parse(manifestXml);

        // простая логика целостности: данные из manifest должны совпасть с session
        if (m.droneId != null && !m.droneId.equals(s.getDroneId()))
            throw new IllegalArgumentException("droneId mismatch: session=" + s.getDroneId() + ", manifest=" + m.droneId);

        s.setStatus("MANIFEST_PARSED");
        sessions.save(s);

        // отправка в catalog (реальная интеграция)
        CatalogImportRequest req = new CatalogImportRequest();
        req.sessionId = s.getSessionId();
        req.droneId = s.getDroneId();
        req.operatorId = s.getOperatorId();
        req.manifestXml = m; // отправим распарсенное (чтобы не гонять строку)
        try {
            restTemplate.postForEntity(
                    catalogBaseUrl + "/api/catalog/import",
                    req,
                    Void.class
            );
        } catch (RestClientException ex) {
            throw new IllegalStateException("Cannot call catalog-service: " + ex.getMessage(), ex);
        }

        return m.photos.size();
    }

    // DTO для передачи в catalog (без временных файлов)
    public static class CatalogImportRequest {
        public String sessionId;
        public String droneId;
        public String operatorId;
        public ManifestStaxParser.Manifest manifestXml;
    }
}
