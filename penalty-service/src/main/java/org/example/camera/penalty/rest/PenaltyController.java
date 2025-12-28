package org.example.camera.penalty.rest;

import org.example.camera.common.dto.PenaltyCheckRequestDto;
import org.example.camera.common.dto.PenaltyCheckResponseDto;
import org.example.camera.penalty.db.MeasurementEntity;
import org.example.camera.penalty.db.MeasurementRepository;
import org.example.camera.penalty.db.PenaltyEntity;
import org.example.camera.penalty.db.PenaltyRepository;
import org.example.camera.penalty.db.ViolationEntity;
import org.example.camera.penalty.db.ViolationRepository;
import org.example.camera.penalty.service.PenaltyDecisionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.time.Instant;

@RestController
@RequestMapping("/api/penalty")
public class PenaltyController {

    private final PenaltyDecisionService decisionService;
    private final MeasurementRepository measurements;
    private final ViolationRepository violations;
    private final PenaltyRepository penalties;

    public PenaltyController(
            PenaltyDecisionService decisionService,
            MeasurementRepository measurements,
            ViolationRepository violations,
            PenaltyRepository penalties
    ) {
        this.decisionService = decisionService;
        this.measurements = measurements;
        this.violations = violations;
        this.penalties = penalties;
    }

    @PostMapping("/check")
    public PenaltyCheckResponseDto check(@RequestBody PenaltyCheckRequestDto req) {
        validate(req);

        PenaltyCheckResponseDto resp = decisionService.decide(req);
        resp.evidenceXml = buildEvidenceXml(req, resp);

        upsertAll(req, resp);
        return resp;
    }

    /**
     * Ручная проверка: разрешена только если ранее было REQUIRES_REVIEW.
     * По итогам ручной проверки requiresReview в Violation сбрасывается в false.
     */
    @PostMapping("/review/{recordId}")
    public PenaltyCheckResponseDto review(@PathVariable String recordId, @RequestBody ReviewRequest body) {
        if (recordId == null || recordId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "recordId is required");
        }
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "review body is required");
        }
        if (body.decisionStatus == null || body.decisionStatus.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decisionStatus is required");
        }
        if (body.inspectorComment == null || body.inspectorComment.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "inspectorComment is required");
        }

        PenaltyEntity p = penalties.findById(recordId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Penalty not found: " + recordId));

        ViolationEntity v = violations.findById(recordId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Violation not found: " + recordId));

        // gate: ручная проверка только если система поставила REQUIRES_REVIEW
        if (!"REQUIRES_REVIEW".equals(p.getDecisionStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "record is not in REQUIRES_REVIEW, current=" + p.getDecisionStatus());
        }

        Instant now = Instant.now();

        // обновляем Violation (там ruleCode и requiresReview)
        if (body.ruleCode != null && !body.ruleCode.isBlank()) {
            v.setRuleCode(body.ruleCode);
        }
        v.setRequiresReview(false);
        v.setUpdatedAt(now);
        violations.save(v);

        // обновляем Penalty (там decision/amount/comment/evidence)
        p.setDecisionStatus(body.decisionStatus);
        if (body.amount != null) {
            p.setAmount(body.amount);
        }
        p.setInspectorComment(body.inspectorComment);
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

        // Measurement (факт измерения)
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

        // Violation (норма/правило + флаг review)
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

        // Penalty (решение + сумма + evidence + коммент инспектора)
        PenaltyEntity p = penalties.findById(req.recordId)
                .orElseGet(() -> {
                    PenaltyEntity x = new PenaltyEntity(req.recordId);
                    x.setCreatedAt(now);
                    return x;
                });

        p.setUpdatedAt(now);
        p.setDecisionStatus(resp.decisionStatus);
        p.setAmount(resp.amount);
        p.setEvidenceXml(resp.evidenceXml);

        // inspectorComment НЕ трогаем в автоматике (оставляем как есть, если уже был)
        penalties.save(p);
    }

    private static void validate(PenaltyCheckRequestDto req) {
        if (req == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        if (req.recordId == null || req.recordId.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "recordId is required");
        if (req.fileKey == null || req.fileKey.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fileKey is required");
        if (!Double.isFinite(req.speed) || req.speed < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "speed must be finite and >= 0");
        if (!Double.isFinite(req.confidence) || req.confidence < 0.0 || req.confidence > 1.0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "confidence must be in [0..1]");
        if (req.location == null || req.location.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "location is required");
        if (req.time == null || req.time.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "time is required");
    }

    private static String buildEvidenceXml(PenaltyCheckRequestDto req, PenaltyCheckResponseDto resp) {
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

            Element root = doc.createElement("evidence");
            doc.appendChild(root);

            append(doc, root, "recordId", req.recordId);
            append(doc, root, "fileKey", req.fileKey);
            append(doc, root, "droneId", req.droneId);
            append(doc, root, "operatorId", req.operatorId);

            append(doc, root, "location", req.location);
            append(doc, root, "time", req.time);

            Element speed = doc.createElement("speed");
            speed.setAttribute("unit", "kmh");
            speed.setTextContent(String.valueOf(req.speed));
            root.appendChild(speed);

            append(doc, root, "confidence", String.valueOf(req.confidence));

            append(doc, root, "decisionStatus", resp.decisionStatus);
            append(doc, root, "ruleCode", resp.ruleCode);
            append(doc, root, "requiresReview", String.valueOf(resp.requiresReview));
            append(doc, root, "amount", String.valueOf(resp.amount));

            StringWriter sw = new StringWriter();
            TransformerFactory.newInstance().newTransformer()
                    .transform(new DOMSource(doc), new StreamResult(sw));
            return sw.toString();
        } catch (Exception e) {
            throw new IllegalStateException("DOM build evidence error: " + e.getMessage(), e);
        }
    }

    private static void append(Document doc, Element parent, String name, String value) {
        Element e = doc.createElement(name);
        e.setTextContent(value == null ? "" : value);
        parent.appendChild(e);
    }

    public static class ReviewRequest {
        public String decisionStatus;   // "APPROVED" / "REJECTED" / "NO_VIOLATION"
        public String ruleCode;         // опционально
        public Double amount;           // опционально
        public String inspectorComment; // обязательно
    }
}
