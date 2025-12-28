package org.example.camera.common.dto;

public class PenaltyReviewRequestDto {
    public String recordId;
    public String decisionStatus;      // "APPROVED"/"REJECTED"/"NO_VIOLATION"
    public String ruleCode;            // опционально
    public Double amount;              // опционально
    public String inspectorComment;    // обязательно
}
