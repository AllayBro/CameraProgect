package org.example.camera.catalog.rest;

import org.example.camera.catalog.db.CatalogRecordEntity;
import org.example.camera.catalog.db.CatalogRecordRepository;
import org.example.camera.common.dto.PenaltyCheckRequestDto;
import org.example.camera.common.dto.PenaltyCheckResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@RestController
@RequestMapping("/api/catalog/records")
public class CatalogRecordOpsController {

    private final CatalogRecordRepository records;
    private final RestTemplate rest = new RestTemplate();

    @Value("${app.penalty.base-url}")
    private String penaltyBaseUrl;

    public CatalogRecordOpsController(CatalogRecordRepository records) {
        this.records = records;
    }

    // Повторный запуск проверки штрафа по уже сохранённой записи (важно для защиты)
    @PostMapping("/{recordId}/run-check")
    public PenaltyCheckResponseDto runCheck(@PathVariable String recordId) {
        CatalogRecordEntity rec = records.findById(recordId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CatalogRecord not found: " + recordId));

        validateRecordForPenalty(rec);

        PenaltyCheckRequestDto req = new PenaltyCheckRequestDto();
        req.recordId = rec.getRecordId();

        req.fileKey = rec.getFileKey();
        req.droneId = rec.getDroneId();
        req.operatorId = rec.getOperatorId();

        req.speed = rec.getSpeedKmh();
        req.confidence = rec.getConfidence();

        req.time = rec.getTakenAt();
        req.location = rec.getLatitude() + "," + rec.getLongitude();

        PenaltyCheckResponseDto resp;
        try {
            resp = rest.postForEntity(
                    penaltyBaseUrl + "/api/penalty/check",
                    req,
                    PenaltyCheckResponseDto.class
            ).getBody();
        } catch (RestClientException ex) {
            rec.setStatus("PENALTY_FAILED");
            rec.setUpdatedAt(Instant.now());
            records.save(rec);

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "penalty-service call failed for recordId=" + recordId + ": " + ex.getMessage(),
                    ex
            );
        }

        if (resp == null) {
            rec.setStatus("PENALTY_FAILED");
            rec.setUpdatedAt(Instant.now());
            records.save(rec);

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "penalty-service returned empty body for recordId=" + recordId
            );
        }

        // сохраняем результат в catalog как данные (не транзит)
        rec.setPenaltyDecisionStatus(resp.decisionStatus);
        rec.setPenaltyRuleCode(resp.ruleCode);
        rec.setPenaltyAmount(resp.amount);
        rec.setPenaltyRequiresReview(resp.requiresReview);
        rec.setEvidenceXml(resp.evidenceXml);

        rec.setStatus("PENALTY_DECIDED");
        rec.setUpdatedAt(Instant.now());
        records.save(rec);

        return resp;
    }

    // Статус/штраф одной строкой (JSON)
    @GetMapping("/{recordId}/status")
    public RecordStatusResponse status(@PathVariable String recordId) {
        CatalogRecordEntity rec = records.findById(recordId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CatalogRecord not found: " + recordId));

        RecordStatusResponse r = new RecordStatusResponse();
        r.recordId = rec.getRecordId();
        r.status = rec.getStatus();
        r.decisionStatus = rec.getPenaltyDecisionStatus();
        r.ruleCode = rec.getPenaltyRuleCode();
        r.amount = rec.getPenaltyAmount();
        r.requiresReview = rec.isPenaltyRequiresReview();
        return r;
    }

    private static void validateRecordForPenalty(CatalogRecordEntity rec) {
        if (rec.getRecordId() == null || rec.getRecordId().isBlank())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "recordId is empty in DB");

        if (rec.getFileKey() == null || rec.getFileKey().isBlank())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "fileKey is empty for recordId=" + rec.getRecordId());

        if (rec.getDroneId() == null || rec.getDroneId().isBlank())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "droneId is empty for recordId=" + rec.getRecordId());

        if (rec.getOperatorId() == null || rec.getOperatorId().isBlank())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "operatorId is empty for recordId=" + rec.getRecordId());

        if (rec.getTakenAt() == null || rec.getTakenAt().isBlank())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "takenAt is empty for recordId=" + rec.getRecordId());

        if (!Double.isFinite(rec.getLatitude()) || !Double.isFinite(rec.getLongitude()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "latitude/longitude invalid for recordId=" + rec.getRecordId());

        if (!Double.isFinite(rec.getSpeedKmh()) || rec.getSpeedKmh() < 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "speedKmh invalid for recordId=" + rec.getRecordId());

        if (!Double.isFinite(rec.getConfidence()) || rec.getConfidence() < 0 || rec.getConfidence() > 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "confidence invalid for recordId=" + rec.getRecordId());
    }

    public static class RecordStatusResponse {
        public String recordId;
        public String status;
        public String decisionStatus;
        public String ruleCode;
        public double amount;
        public boolean requiresReview;
    }
}
