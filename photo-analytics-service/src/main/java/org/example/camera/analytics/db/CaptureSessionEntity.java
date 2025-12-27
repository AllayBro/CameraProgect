package org.example.camera.analytics.db;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "capture_session")
public class CaptureSessionEntity {

    @Id
    private String sessionId;

    private String droneId;
    private String operatorId;

    private Instant startTime;
    private Instant endTime;

    private String packageChecksum;
    private String status;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getDroneId() { return droneId; }
    public void setDroneId(String droneId) { this.droneId = droneId; }

    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }

    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }

    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }

    public String getPackageChecksum() { return packageChecksum; }
    public void setPackageChecksum(String packageChecksum) { this.packageChecksum = packageChecksum; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
