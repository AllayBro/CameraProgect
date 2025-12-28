package org.example.camera.analytics.soap;

import jakarta.jws.WebService;
import org.example.camera.analytics.db.CaptureSessionEntity;
import org.example.camera.analytics.service.AnalyticsService;
import org.example.camera.common.dto.StartSessionRequestDto;
import org.springframework.stereotype.Service;

@Service
@WebService(
        serviceName = "AnalyticsSoapService",
        portName = "AnalyticsSoapPort",
        endpointInterface = "org.example.camera.analytics.soap.AnalyticsSoapApi",
        targetNamespace = "http://camera.example.org/analytics"
)
public class AnalyticsSoapApiImpl implements AnalyticsSoapApi {

    private final AnalyticsService analytics;

    public AnalyticsSoapApiImpl(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @Override
    public String startSession(String droneId, String operatorId) {
        CaptureSessionEntity s = analytics.startSession(droneId, operatorId);
        return s.getSessionId();
    }

    @Override
    public String getSessionStatus(String sessionId) {
        return analytics.getSession(sessionId).getStatus();
    }

    @Override
    public String startSessionEx(StartSessionRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (request.drone == null || request.drone.droneId == null || request.drone.droneId.isBlank()) {
            throw new IllegalArgumentException("drone.droneId is required");
        }
        if (request.operatorId == null || request.operatorId.isBlank()) {
            throw new IllegalArgumentException("operatorId is required");
        }

        return analytics.startSession(request.drone.droneId, request.operatorId).getSessionId();
    }
}
