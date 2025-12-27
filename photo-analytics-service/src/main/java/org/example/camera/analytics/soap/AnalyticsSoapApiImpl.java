package org.example.camera.analytics.soap;

import org.example.camera.analytics.db.CaptureSessionEntity;
import org.example.camera.analytics.service.AnalyticsService;
import org.springframework.stereotype.Service;

import jakarta.jws.WebService;



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
}
