package org.example.camera.penalty.rest;

import org.example.camera.common.dto.PenaltyCheckResponseDto;
import org.example.camera.penalty.db.PenaltyEntity;
import org.example.camera.penalty.db.PenaltyRepository;
import org.example.camera.penalty.db.ViolationEntity;
import org.example.camera.penalty.db.ViolationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/penalty")
public class PenaltyQueryController {

    private final PenaltyRepository penalties;
    private final ViolationRepository violations;

    public PenaltyQueryController(PenaltyRepository penalties, ViolationRepository violations) {
        this.penalties = penalties;
        this.violations = violations;
    }

    @GetMapping("/checks/{recordId}")
    public PenaltyCheckResponseDto getCheck(@PathVariable String recordId) {
        if (recordId == null || recordId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "recordId is required");
        }

        PenaltyEntity p = penalties.findById(recordId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Penalty not found: " + recordId));

        ViolationEntity v = violations.findById(recordId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Violation not found: " + recordId));

        PenaltyCheckResponseDto resp = new PenaltyCheckResponseDto();
        resp.recordId = recordId;
        resp.decisionStatus = p.getDecisionStatus();
        resp.amount = p.getAmount();
        resp.evidenceXml = p.getEvidenceXml();

        resp.ruleCode = v.getRuleCode();
        resp.requiresReview = v.isRequiresReview();

        return resp;
    }
}
