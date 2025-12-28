package org.example.camera.penalty.soap;

import jakarta.jws.WebService;
import org.example.camera.common.dto.*;
import org.example.camera.penalty.db.*;
import org.example.camera.penalty.service.PenaltyDecisionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
@WebService(
        serviceName = "PenaltySoapService",
        portName = "PenaltySoapPort",
        endpointInterface = "org.example.camera.penalty.soap.PenaltySoapApi",
        targetNamespace = "http://camera.example.org/penalty"
)
public class PenaltySoapApiImpl implements PenaltySoapApi {

    private final PenaltyDecisionService decisionService;
    private final MeasurementRepository measurements;
    private final ViolationRepository violations;
    private final PenaltyRepository penalties;

    public PenaltySoapApiImpl(PenaltyDecisionService decisionService,
                              MeasurementRepository measurements,
                              ViolationRepository violations,
                              PenaltyRepository penalties) {
        this.decisionService = decisionService;
        this.measurements = measurements;
        this.violations = violations;
        this.penalties = penalties;
    }

    @Override
    public PenaltyCheckResponseDto runCheck(PenaltyCheckRequestDto request) {
        if (request == null) throw bad("request is required");
        // валидацию делайте той же, что REST (recordId, fileKey, speed, confidence, location, time)
        PenaltyCheckResponseDto resp = decisionService.decide(request);
        // evidenceXml формируется REST-слоем у Вас; если хотите — можно вынести builder в утилиту и использовать тут же
        upsertAll(request, resp);
        return resp;
    }

    @Override
    public PenaltyCheckResponseDto getCheckStatus(String recordId) {
        if (recordId == null || recordId.isBlank()) throw bad("recordId is required");

        PenaltyEntity p = penalties.findById(recordId)
                .orElseThrow(() -> bad("Penalty not found: " + recordId));
        ViolationEntity v = violations.findById(recordId)
                .orElseThrow(() -> bad("Violation not found: " + recordId));

        PenaltyCheckResponseDto resp = new PenaltyCheckResponseDto();
        resp.recordId = recordId;
        resp.decisionStatus = p.getDecisionStatus();
        resp.amount = p.getAmount();
        resp.evidenceXml = p.getEvidenceXml();
        resp.ruleCode = v.getRuleCode();
        resp.requiresReview = v.isRequiresReview();
        return resp;
    }

    @Override
    public ViolationDto getViolation(String recordId) {
        if (recordId == null || recordId.isBlank()) throw bad("recordId is required");

        ViolationEntity v = violations.findById(recordId)
                .orElseThrow(() -> bad("Violation not found: " + recordId));

        ViolationDto dto = new ViolationDto();
        dto.recordId = recordId;
        dto.ruleCode = v.getRuleCode();
        dto.requiresReview = v.isRequiresReview();
        dto.confidence = v.getConfidence();
        return dto;
    }

    @Override
    public PenaltyCheckResponseDto submitReview(PenaltyReviewRequestDto request) {
        if (request == null) throw bad("request is required");
        if (request.recordId == null || request.recordId.isBlank()) throw bad("recordId is required");
        if (request.decisionStatus == null || request.decisionStatus.isBlank()) throw bad("decisionStatus is required");
        if (request.inspectorComment == null || request.inspectorComment.isBlank()) throw bad("inspectorComment is required");

        String recordId = request.recordId;

        PenaltyEntity p = penalties.findById(recordId)
                .orElseThrow(() -> bad("Penalty not found: " + recordId));
        ViolationEntity v = violations.findById(recordId)
                .orElseThrow(() -> bad("Violation not found: " + recordId));

        if (!"REQUIRES_REVIEW".equals(p.getDecisionStatus())) {
            throw bad("record is not in REQUIRES_REVIEW, current=" + p.getDecisionStatus());
        }

        Instant now = Instant.now();

        if (request.ruleCode != null && !request.ruleCode.isBlank()) {
            v.setRuleCode(request.ruleCode);
        }
        v.setRequiresReview(false);
        v.setUpdatedAt(now);
        violations.save(v);

        p.setDecisionStatus(request.decisionStatus);
        if (request.amount != null) p.setAmount(request.amount);
        p.setInspectorComment(request.inspectorComment);
        p.setUpdatedAt(now);
        penalties.save(p);

        PenaltyCheckResponseDto resp = new PenaltyCheckResponseDto();
        resp.recordId = recordId;
        resp.decisionStatus = p.getDecisionStatus();
        resp.amount = p.getAmount();
        resp.ruleCode = v.getRuleCode();
        resp.requiresReview = false;
        resp.evidenceXml = p.getEvidenceXml();
        return resp;
    }

    private void upsertAll(PenaltyCheckRequestDto req, PenaltyCheckResponseDto resp) {
        Instant now = Instant.now();

        MeasurementEntity m = measurements.findById(req.recordId)
                .orElseGet(() -> {
                    MeasurementEntity x = new MeasurementEntity(req.recordId);
                    x.setCreatedAt(now);
                    return x;
                });

        m.setUpdatedAt(now);
        m.setFileKey(req.fileKey);
        m.setDroneId(req.droneId);
        m.setOperatorId(req.operatorId);
        m.setSpeed(req.speed);
        m.setLocation(req.location);
        m.setTime(req.time);
        m.setConfidence(req.confidence);
        measurements.save(m);

        ViolationEntity v = violations.findById(req.recordId)
                .orElseGet(() -> {
                    ViolationEntity x = new ViolationEntity(req.recordId);
                    x.setCreatedAt(now);
                    return x;
                });

        v.setUpdatedAt(now);
        v.setRuleCode(resp.ruleCode);
        v.setRequiresReview(resp.requiresReview);
        v.setConfidence(req.confidence);
        violations.save(v);

        PenaltyEntity p = penalties.findById(req.recordId)
                .orElseGet(() -> {
                    PenaltyEntity x = new PenaltyEntity(req.recordId);
                    x.setCreatedAt(now);
                    return x;
                });

        p.setUpdatedAt(now);
        p.setDecisionStatus(resp.decisionStatus);
        p.setAmount(resp.amount);
        // evidenceXml хранится у Вас в REST-контроллере; если хотите — перенесите builder сюда же
        penalties.save(p);
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
