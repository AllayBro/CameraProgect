package org.example.camera.catalog.db;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "catalog_record")
public class CatalogRecordEntity {

    @Id
    @Column(name = "record_id", nullable = false, length = 200)
    private String recordId; // sessionId:fileKey

    @Column(nullable = false, length = 120)
    private String sessionId;

    @Column(nullable = false, length = 200)
    private String fileKey;

    @Column(nullable = false, length = 120)
    private String droneId;

    @Column(nullable = false, length = 120)
    private String operatorId;

    @Column(nullable = false, length = 64)
    private String status;

    @Column(nullable = false, length = 64)
    private String takenAt; // xs:dateTime строкой из manifest

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false)
    private double altitude;

    @Column(nullable = false)
    private double speedKmh;

    @Column(nullable = false)
    private double distanceMeters;

    @Column(nullable = false)
    private double confidence;

    @Column(length = 80)
    private String objectType;

    @Column(length = 80)
    private String modelVersion;

    // решение penalty
    @Column(length = 64)
    private String penaltyDecisionStatus;

    @Column(length = 64)
    private String penaltyRuleCode;

    @Column(nullable = false)
    private double penaltyAmount;

    @Column(nullable = false)
    private boolean penaltyRequiresReview;

    @Lob
    @Column
    private String evidenceXml;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public CatalogRecordEntity() {}

    public CatalogRecordEntity(String recordId) {
        this.recordId = recordId;
    }

    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getFileKey() { return fileKey; }
    public void setFileKey(String fileKey) { this.fileKey = fileKey; }

    public String getDroneId() { return droneId; }
    public void setDroneId(String droneId) { this.droneId = droneId; }

    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTakenAt() { return takenAt; }
    public void setTakenAt(String takenAt) { this.takenAt = takenAt; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public double getAltitude() { return altitude; }
    public void setAltitude(double altitude) { this.altitude = altitude; }

    public double getSpeedKmh() { return speedKmh; }
    public void setSpeedKmh(double speedKmh) { this.speedKmh = speedKmh; }

    public double getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(double distanceMeters) { this.distanceMeters = distanceMeters; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }

    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }

    public String getPenaltyDecisionStatus() { return penaltyDecisionStatus; }
    public void setPenaltyDecisionStatus(String penaltyDecisionStatus) { this.penaltyDecisionStatus = penaltyDecisionStatus; }

    public String getPenaltyRuleCode() { return penaltyRuleCode; }
    public void setPenaltyRuleCode(String penaltyRuleCode) { this.penaltyRuleCode = penaltyRuleCode; }

    public double getPenaltyAmount() { return penaltyAmount; }
    public void setPenaltyAmount(double penaltyAmount) { this.penaltyAmount = penaltyAmount; }

    public boolean isPenaltyRequiresReview() { return penaltyRequiresReview; }
    public void setPenaltyRequiresReview(boolean penaltyRequiresReview) { this.penaltyRequiresReview = penaltyRequiresReview; }

    public String getEvidenceXml() { return evidenceXml; }
    public void setEvidenceXml(String evidenceXml) { this.evidenceXml = evidenceXml; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
