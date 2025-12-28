package org.example.camera.penalty.rules;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "penalty_rule_set")
public class RuleSetEntity {

    @Id
    @Column(name = "rules_id", nullable = false, length = 64)
    private String id; // "active"

    @Lob
    @Column(nullable = false)
    private String xml;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public RuleSetEntity() {}

    public RuleSetEntity(String id) { this.id = id; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getXml() { return xml; }
    public void setXml(String xml) { this.xml = xml; }

    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
