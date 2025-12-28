package org.example.camera.common.dto;

public class PenaltyCheckResponseDto {

    public String recordId;

    // например: NO_VIOLATION / APPROVED / REQUIRES_REVIEW
    public String decisionStatus;

    // например: SPEED_LIMIT / NO_VIOLATION
    public String ruleCode;

    public double amount;

    public boolean requiresReview;

    // XML строкой
    public String evidenceXml;
}
