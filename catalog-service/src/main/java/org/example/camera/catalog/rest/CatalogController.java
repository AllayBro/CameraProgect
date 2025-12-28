package org.example.camera.catalog.rest;

import org.example.camera.catalog.db.CatalogRecordEntity;
import org.example.camera.catalog.db.CatalogRecordRepository;
import org.example.camera.common.dto.AnalysisResultDto;
import org.example.camera.common.dto.CatalogImportRequestDto;
import org.example.camera.common.dto.PenaltyCheckRequestDto;
import org.example.camera.common.dto.PenaltyCheckResponseDto;
import org.example.camera.common.dto.PhotoDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final RestTemplate rest = new RestTemplate();
    private final CatalogRecordRepository records;

    @Value("${app.penalty.base-url}")
    private String penaltyBaseUrl;

    public CatalogController(CatalogRecordRepository records) {
        this.records = records;
    }

    // ---------- IMPORT (analytics -> catalog -> penalty) ----------

    @PostMapping("/import")
    public String importFromAnalytics(@RequestBody CatalogImportRequestDto req) {
        validateImport(req);

        for (PhotoDto p : req.manifest.photos) {
            validatePhoto(p);

            String recordId = req.sessionId + ":" + p.fileKey;
            AnalysisResultDto ar = findResultOrThrow(req, p.fileKey);

            CatalogRecordEntity rec = records.findById(recordId).orElseGet(() -> {
                CatalogRecordEntity x = new CatalogRecordEntity(recordId);
                x.setCreatedAt(Instant.now());
                return x;
            });

            rec.setUpdatedAt(Instant.now());
            rec.setSessionId(req.sessionId);
            rec.setFileKey(p.fileKey);
            rec.setDroneId(req.manifest.droneId);
            rec.setOperatorId(req.manifest.operatorId);

            rec.setTakenAt(p.takenAt);
            rec.setLatitude(p.latitude);
            rec.setLongitude(p.longitude);
            rec.setAltitude(p.altitude);

            rec.setSpeedKmh(ar.speed);
            rec.setDistanceMeters(ar.distance);
            rec.setConfidence(ar.confidence);
            rec.setObjectType(ar.objectType);
            rec.setModelVersion(ar.modelVersion);

            if (rec.getStatus() == null) rec.setStatus("RECEIVED");

            // Идемпотентность: если решение уже сохранено — повторно penalty не вызываем
            if ("PENALTY_DECIDED".equals(rec.getStatus())) {
                records.save(rec);
                continue;
            }

            rec.setStatus("SENT_TO_PENALTY");
            records.save(rec);

            PenaltyCheckRequestDto r = new PenaltyCheckRequestDto();
            r.recordId = recordId;
            r.fileKey = p.fileKey;
            r.droneId = req.manifest.droneId;
            r.operatorId = req.manifest.operatorId;
            r.speed = ar.speed;
            r.confidence = ar.confidence;
            r.time = p.takenAt;
            r.location = p.latitude + "," + p.longitude;

            PenaltyCheckResponseDto resp;
            try {
                resp = rest.postForEntity(
                        penaltyBaseUrl + "/api/penalty/check",
                        r,
                        PenaltyCheckResponseDto.class
                ).getBody();
            } catch (RestClientException ex) {
                rec.setStatus("PENALTY_FAILED");
                rec.setUpdatedAt(Instant.now());
                records.save(rec);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "penalty-service call failed for recordId=" + recordId + ": " + ex.getMessage(), ex);
            }

            if (resp == null) {
                rec.setStatus("PENALTY_FAILED");
                rec.setUpdatedAt(Instant.now());
                records.save(rec);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "penalty-service returned empty body for recordId=" + recordId);
            }

            rec.setPenaltyDecisionStatus(resp.decisionStatus);
            rec.setPenaltyRuleCode(resp.ruleCode);
            rec.setPenaltyAmount(resp.amount);
            rec.setPenaltyRequiresReview(resp.requiresReview);
            rec.setEvidenceXml(resp.evidenceXml);

            rec.setStatus("PENALTY_DECIDED");
            rec.setUpdatedAt(Instant.now());
            records.save(rec);
        }

        return "IMPORTED photos=" + req.manifest.photos.size();
    }

    // ---------- API чтения записи каталога ----------

    @GetMapping("/records/{recordId}")
    public CatalogRecordEntity getRecord(@PathVariable String recordId) {
        if (recordId == null || recordId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "recordId is required");
        }
        return records.findById(recordId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CatalogRecord not found: " + recordId));
    }

    // XML-представление записи (для XPath-демо)
    @GetMapping(value = "/records/{recordId}/xml", produces = "application/xml")
    public String getRecordXml(@PathVariable String recordId) {
        CatalogRecordEntity rec = getRecord(recordId);
        return toCatalogRecordXml(rec);
    }

    // ---------- XPath поиск по XML-представлению записей ----------

    // Возвращает recordId, для которых XPath даёт непустой nodeset ИЛИ boolean=true
    @GetMapping("/search")
    public List<String> searchByXpath(@RequestParam("xpath") String xpathExpr) {
        if (xpathExpr == null || xpathExpr.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "xpath is required");
        }

        XPathExpression compiled = compileXpathOr400(xpathExpr);
        List<String> out = new ArrayList<>();

        for (CatalogRecordEntity rec : records.findAll()) {
            Document doc = toCatalogRecordDom(rec);

            try {
                Object nodes = compiled.evaluate(doc, XPathConstants.NODESET);
                if (nodes instanceof NodeList nl && nl.getLength() > 0) {
                    out.add(rec.getRecordId());
                    continue;
                }
            } catch (Exception ignored) {
                // выражение может быть boolean/string — пробуем boolean ниже
            }

            try {
                Boolean b = (Boolean) compiled.evaluate(doc, XPathConstants.BOOLEAN);
                if (Boolean.TRUE.equals(b)) out.add(rec.getRecordId());
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid XPath: " + e.getMessage(), e);
            }
        }

        return out;
    }

    // ---------- helpers ----------

    private static void validateImport(CatalogImportRequestDto req) {
        if (req == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        if (req.sessionId == null || req.sessionId.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId is required");
        if (req.manifest == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "manifest is required");
        if (req.manifest.droneId == null || req.manifest.droneId.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "manifest.droneId is required");
        if (req.manifest.operatorId == null || req.manifest.operatorId.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "manifest.operatorId is required");
        if (req.manifest.photos == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "manifest.photos is required");
        if (req.analysisResults == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "analysisResults is required (no fallbacks allowed)");
    }

    private static void validatePhoto(PhotoDto p) {
        if (p == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "photo entry is null");
        if (p.fileKey == null || p.fileKey.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "photo.fileKey is required");
        if (p.takenAt == null || p.takenAt.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "photo.takenAt is required for fileKey=" + p.fileKey);
        if (!Double.isFinite(p.latitude) || !Double.isFinite(p.longitude)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "photo latitude/longitude must be finite for fileKey=" + p.fileKey);
        }
    }

    private static AnalysisResultDto findResultOrThrow(CatalogImportRequestDto req, String fileKey) {
        for (AnalysisResultDto ar : req.analysisResults) {
            if (ar != null && fileKey.equals(ar.fileKey)) return ar;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "analysisResults missing for fileKey=" + fileKey);
    }

    private static XPathExpression compileXpathOr400(String expr) {
        try {
            return XPathFactory.newInstance().newXPath().compile(expr);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid XPath: " + e.getMessage(), e);
        }
    }

    private static String toCatalogRecordXml(CatalogRecordEntity rec) {
        try {
            Document doc = toCatalogRecordDom(rec);
            StringWriter sw = new StringWriter();
            TransformerFactory.newInstance().newTransformer()
                    .transform(new DOMSource(doc), new StreamResult(sw));
            return sw.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot build record xml: " + e.getMessage(), e);
        }
    }

    private static Document toCatalogRecordDom(CatalogRecordEntity rec) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);

            // защита: никакого внешнего DTD/ENTITY
            trySet(dbf, XMLConstants.FEATURE_SECURE_PROCESSING, true);
            trySet(dbf, "http://apache.org/xml/features/disallow-doctype-decl", true);
            trySet(dbf, "http://xml.org/sax/features/external-general-entities", false);
            trySet(dbf, "http://xml.org/sax/features/external-parameter-entities", false);
            trySet(dbf, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            Document doc = dbf.newDocumentBuilder().newDocument();

            Element catalog = doc.createElement("catalog");
            doc.appendChild(catalog);

            Element record = doc.createElement("record");
            catalog.appendChild(record);

            append(doc, record, "recordId", rec.getRecordId());
            append(doc, record, "sessionId", rec.getSessionId());
            append(doc, record, "fileKey", rec.getFileKey());

            append(doc, record, "droneId", rec.getDroneId());
            append(doc, record, "operatorId", rec.getOperatorId());

            append(doc, record, "takenAt", rec.getTakenAt());
            append(doc, record, "latitude", String.valueOf(rec.getLatitude()));
            append(doc, record, "longitude", String.valueOf(rec.getLongitude()));
            append(doc, record, "altitude", String.valueOf(rec.getAltitude()));

            append(doc, record, "speedKmh", String.valueOf(rec.getSpeedKmh()));
            append(doc, record, "distanceMeters", String.valueOf(rec.getDistanceMeters()));
            append(doc, record, "confidence", String.valueOf(rec.getConfidence()));

            append(doc, record, "status", rec.getStatus());

            append(doc, record, "decisionStatus", rec.getPenaltyDecisionStatus());
            append(doc, record, "ruleCode", rec.getPenaltyRuleCode());
            append(doc, record, "amount", String.valueOf(rec.getPenaltyAmount()));
            append(doc, record, "requiresReview", String.valueOf(rec.isPenaltyRequiresReview()));

            append(doc, record, "evidenceXml", rec.getEvidenceXml());

            return doc;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot build DOM: " + e.getMessage(), e);
        }
    }

    private static void append(Document doc, Element parent, String name, String value) {
        Element e = doc.createElement(name);
        e.setTextContent(value == null ? "" : value);
        parent.appendChild(e);
    }

    private static void trySet(DocumentBuilderFactory dbf, String feature, boolean value) {
        try { dbf.setFeature(feature, value); } catch (Exception ignored) {}
    }
}
