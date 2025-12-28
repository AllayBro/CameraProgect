package org.example.camera.penalty.db;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "penalty")
public class PenaltyEntity {

    @Id
    private String recordId;

    private double amount;
    private String decisionStatus;

    @Lob
    private String evidenceXml;

    private String inspectorComment;

    private Instant createdAt;
    private Instant updatedAt;

    public PenaltyEntity() {}

    public PenaltyEntity(String recordId) {
        this.recordId = recordId;
    }

    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getDecisionStatus() { return decisionStatus; }
    public void setDecisionStatus(String decisionStatus) { this.decisionStatus = decisionStatus; }

    public String getEvidenceXml() { return evidenceXml; }
    public void setEvidenceXml(String evidenceXml) { this.evidenceXml = evidenceXml; }

    public String getInspectorComment() { return inspectorComment; }
    public void setInspectorComment(String inspectorComment) { this.inspectorComment = inspectorComment; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
