package org.example.camera.catalog.soap;

import jakarta.jws.WebService;
import org.example.camera.catalog.db.CatalogRecordEntity;
import org.example.camera.catalog.db.CatalogRecordRepository;
import org.example.camera.catalog.rules.RuleSetEntity;
import org.example.camera.catalog.rules.RuleSetRepository;
import org.example.camera.common.dto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

@Service
@WebService(
        endpointInterface = "org.example.camera.catalog.soap.CatalogSoapApi",
        targetNamespace = "http://camera.example.org/catalog",
        serviceName = "CatalogSoapService",
        portName = "CatalogSoapPort"
)
public class CatalogSoapApiImpl implements CatalogSoapApi {

    private static final String ACTIVE_RULES_ID = "active";
    private static final String RULES_XSD = "contracts/rules/rules.xsd";

    private final CatalogRecordRepository records;
    private final RuleSetRepository rulesRepo;
    private final RestTemplate rest = new RestTemplate();

    @Value("${app.penalty.base-url}")
    private String penaltyBaseUrl;

    public CatalogSoapApiImpl(CatalogRecordRepository records, RuleSetRepository rulesRepo) {
        this.records = records;
        this.rulesRepo = rulesRepo;
    }

    @Override
    public CatalogImportResponseDto importCatalog(CatalogImportRequestDto req) {
        // тот же контракт, что REST: без fallbacks
        if (req == null) throw soapBad("request is required");
        if (req.sessionId == null || req.sessionId.isBlank()) throw soapBad("sessionId is required");
        if (req.manifest == null || req.manifest.photos == null) throw soapBad("manifest.photos is required");
        if (req.analysisResults == null) throw soapBad("analysisResults is required (no fallbacks)");

        int count = req.manifest.photos.size();

        for (PhotoDto p : req.manifest.photos) {
            if (p == null || p.fileKey == null || p.fileKey.isBlank()) throw soapBad("photo.fileKey is required");
            AnalysisResultDto ar = findResultOrThrow(req, p.fileKey);

            String recordId = req.sessionId + ":" + p.fileKey;

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

            // идемпотентность: если уже решили — не дергаем penalty
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
                throw soapBad("penalty-service call failed for recordId=" + recordId + ": " + ex.getMessage());
            }

            if (resp == null) throw soapBad("penalty-service returned empty body for recordId=" + recordId);

            rec.setPenaltyDecisionStatus(resp.decisionStatus);
            rec.setPenaltyRuleCode(resp.ruleCode);
            rec.setPenaltyAmount(resp.amount);
            rec.setPenaltyRequiresReview(resp.requiresReview);
            rec.setEvidenceXml(resp.evidenceXml);

            rec.setStatus("PENALTY_DECIDED");
            rec.setUpdatedAt(Instant.now());
            records.save(rec);
        }

        CatalogImportResponseDto resp = new CatalogImportResponseDto();
        resp.sessionId = req.sessionId;
        resp.status = "IMPORTED";
        resp.photosImported = count;
        return resp;
    }

    @Override
    public String getRecordXml(String recordId) {
        if (recordId == null || recordId.isBlank()) throw soapBad("recordId is required");

        CatalogRecordEntity rec = records.findById(recordId)
                .orElseThrow(() -> soapBad("CatalogRecord not found: " + recordId));

        return buildRecordXml(rec);
    }

    @Override
    public String importRules(byte[] rulesXml) {
        if (rulesXml == null || rulesXml.length == 0) throw soapBad("rulesXml is empty");

        validateRulesXsd(rulesXml);

        String sha = sha256Hex(rulesXml);
        String xml = new String(rulesXml, StandardCharsets.UTF_8);
        Instant now = Instant.now();

        RuleSetEntity e = rulesRepo.findById(ACTIVE_RULES_ID).orElseGet(() -> {
            RuleSetEntity x = new RuleSetEntity(ACTIVE_RULES_ID);
            x.setCreatedAt(now);
            return x;
        });

        e.setUpdatedAt(now);
        e.setSha256(sha);
        e.setXml(xml);
        rulesRepo.save(e);

        return "OK sha256=" + sha;
    }

    @Override
    public byte[] exportRules() {
        RuleSetEntity e = rulesRepo.findById(ACTIVE_RULES_ID)
                .orElseThrow(() -> soapBad("rules not uploaded"));
        return e.getXml().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String[] searchRules(String xpath) {
        if (xpath == null || xpath.isBlank()) throw soapBad("xpath is required");

        RuleSetEntity e = rulesRepo.findById(ACTIVE_RULES_ID)
                .orElseThrow(() -> soapBad("rules not uploaded"));

        Document doc = parseXmlSecure(e.getXml());
        XPathExpression compiled = compileXpathOrBad(xpath);

        try {
            NodeList nl = (NodeList) compiled.evaluate(doc, XPathConstants.NODESET);
            String[] out = new String[nl.getLength()];
            for (int i = 0; i < nl.getLength(); i++) out[i] = nl.item(i).getTextContent();
            return out;
        } catch (Exception ex) {
            throw soapBad("XPath evaluation error: " + ex.getMessage());
        }
    }

    private static AnalysisResultDto findResultOrThrow(CatalogImportRequestDto req, String fileKey) {
        for (AnalysisResultDto ar : req.analysisResults) {
            if (ar != null && fileKey.equals(ar.fileKey)) return ar;
        }
        throw soapBad("analysisResults missing for fileKey=" + fileKey);
    }

    private static String buildRecordXml(CatalogRecordEntity rec) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            trySet(dbf, XMLConstants.FEATURE_SECURE_PROCESSING, true);
            trySet(dbf, "http://apache.org/xml/features/disallow-doctype-decl", true);
            trySet(dbf, "http://xml.org/sax/features/external-general-entities", false);
            trySet(dbf, "http://xml.org/sax/features/external-parameter-entities", false);
            trySet(dbf, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            Document doc = dbf.newDocumentBuilder().newDocument();

            Element record = doc.createElement("record");
            doc.appendChild(record);

            append(doc, record, "recordId", rec.getRecordId());
            append(doc, record, "sessionId", rec.getSessionId());
            append(doc, record, "fileKey", rec.getFileKey());
            append(doc, record, "droneId", rec.getDroneId());
            append(doc, record, "operatorId", rec.getOperatorId());
            append(doc, record, "takenAt", rec.getTakenAt());
            append(doc, record, "latitude", String.valueOf(rec.getLatitude()));
            append(doc, record, "longitude", String.valueOf(rec.getLongitude()));
            append(doc, record, "speedKmh", String.valueOf(rec.getSpeedKmh()));
            append(doc, record, "confidence", String.valueOf(rec.getConfidence()));
            append(doc, record, "status", rec.getStatus());

            append(doc, record, "decisionStatus", rec.getPenaltyDecisionStatus());
            append(doc, record, "ruleCode", rec.getPenaltyRuleCode());
            append(doc, record, "amount", String.valueOf(rec.getPenaltyAmount()));
            append(doc, record, "requiresReview", String.valueOf(rec.isPenaltyRequiresReview()));
            append(doc, record, "evidenceXml", rec.getEvidenceXml());

            StringWriter sw = new StringWriter();
            TransformerFactory.newInstance().newTransformer()
                    .transform(new DOMSource(doc), new StreamResult(sw));
            return sw.toString();
        } catch (Exception e) {
            throw soapBad("cannot build record xml: " + e.getMessage());
        }
    }

    private void validateRulesXsd(byte[] xmlBytes) {
        try (var xsdStream = getClass().getClassLoader().getResourceAsStream(RULES_XSD)) {
            if (xsdStream == null) throw soapBad("XSD not found: " + RULES_XSD);

            var sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            try {
                sf.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                sf.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            } catch (Exception ignored) {}

            var schema = sf.newSchema(new StreamSource(xsdStream));
            Validator v = schema.newValidator();

            var spf = javax.xml.parsers.SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            trySetFeature(spf, "http://xml.org/sax/features/external-general-entities", false);
            trySetFeature(spf, "http://xml.org/sax/features/external-parameter-entities", false);
            trySetFeature(spf, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            v.validate(new SAXSource(spf.newSAXParser().getXMLReader(), new InputSource(new ByteArrayInputStream(xmlBytes))));
        } catch (SAXParseException e) {
            throw soapBad("rules.xml invalid by XSD: line=" + e.getLineNumber() + ", col=" + e.getColumnNumber() + ", msg=" + e.getMessage());
        } catch (Exception e) {
            throw soapBad("rules.xml validation failed: " + e.getMessage());
        }
    }

    private static Document parseXmlSecure(String xml) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            trySet(dbf, XMLConstants.FEATURE_SECURE_PROCESSING, true);
            trySet(dbf, "http://apache.org/xml/features/disallow-doctype-decl", true);
            trySet(dbf, "http://xml.org/sax/features/external-general-entities", false);
            trySet(dbf, "http://xml.org/sax/features/external-parameter-entities", false);
            trySet(dbf, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            return dbf.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw soapBad("Cannot parse xml: " + e.getMessage());
        }
    }

    private static XPathExpression compileXpathOrBad(String expr) {
        try {
            return XPathFactory.newInstance().newXPath().compile(expr);
        } catch (Exception e) {
            throw soapBad("Invalid XPath: " + e.getMessage());
        }
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(data);
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw soapBad("SHA-256 error: " + e.getMessage());
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

    private static void trySetFeature(javax.xml.parsers.SAXParserFactory spf, String feature, boolean value) {
        try { spf.setFeature(feature, value); } catch (Exception ignored) {}
    }

    private static ResponseStatusException soapBad(String msg) {
        // CXF превратит это в SOAP Fault
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
