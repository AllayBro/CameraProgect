package org.example.camera.catalog.rest;

import org.example.camera.catalog.db.CatalogRecordEntity;
import org.example.camera.catalog.db.CatalogRecordRepository;
import org.example.camera.catalog.rules.RuleSetEntity;
import org.example.camera.catalog.rules.RuleSetRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;

@RestController
@RequestMapping("/api/catalog")
public class ReportController {

    private static final String ACTIVE_RULES_ID = "active";
    private static final String XSL_CLASSPATH = "report/report.xsl";

    private final CatalogRecordRepository records;
    private final RuleSetRepository rulesRepo;

    public ReportController(CatalogRecordRepository records, RuleSetRepository rulesRepo) {
        this.records = records;
        this.rulesRepo = rulesRepo;
    }

    @GetMapping(value = "/reports/{recordId}.html", produces = "text/html; charset=UTF-8")
    public String report(@PathVariable String recordId) {
        CatalogRecordEntity rec = records.findById(recordId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CatalogRecord not found: " + recordId));

        RuleSetEntity rules = rulesRepo.findById(ACTIVE_RULES_ID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "rules.xml is not uploaded to catalog-service"));

        Document caseDoc = buildCaseDom(rec, rules.getXml());

        try (var xsl = getClass().getClassLoader().getResourceAsStream(XSL_CLASSPATH)) {
            if (xsl == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "XSL not found: " + XSL_CLASSPATH);
            }

            StringWriter sw = new StringWriter();
            TransformerFactory.newInstance()
                    .newTransformer(new StreamSource(xsl))
                    .transform(new DOMSource(caseDoc), new StreamResult(sw));
            return sw.toString();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "XSLT error: " + e.getMessage(), e);
        }
    }

    @GetMapping(value = "/cases/{recordId}.xml", produces = "application/xml; charset=UTF-8")
    public String caseXml(@PathVariable String recordId) {
        CatalogRecordEntity rec = records.findById(recordId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CatalogRecord not found: " + recordId));

        RuleSetEntity rules = rulesRepo.findById(ACTIVE_RULES_ID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "rules.xml is not uploaded to catalog-service"));

        Document caseDoc = buildCaseDom(rec, rules.getXml());

        try {
            StringWriter sw = new StringWriter();
            TransformerFactory.newInstance().newTransformer()
                    .transform(new DOMSource(caseDoc), new StreamResult(sw));
            return sw.toString();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "case.xml build error: " + e.getMessage(), e);
        }
    }

    private static Document buildCaseDom(CatalogRecordEntity rec, String rulesXml) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            try { dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true); } catch (Exception ignored) {}
            try { dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception ignored) {}
            try { dbf.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Exception ignored) {}
            try { dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Exception ignored) {}
            try { dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false); } catch (Exception ignored) {}

            Document doc = dbf.newDocumentBuilder().newDocument();
            Element root = doc.createElement("case");
            doc.appendChild(root);

            // record
            Element record = doc.createElement("record");
            root.appendChild(record);

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
            append(doc, record, "confidence", String.valueOf(rec.getConfidence()));
            append(doc, record, "decisionStatus", rec.getPenaltyDecisionStatus());
            append(doc, record, "ruleCode", rec.getPenaltyRuleCode());
            append(doc, record, "amount", String.valueOf(rec.getPenaltyAmount()));
            append(doc, record, "requiresReview", String.valueOf(rec.isPenaltyRequiresReview()));
            append(doc, record, "evidenceXml", rec.getEvidenceXml());

            // rules (парсим и импортируем узел)
            Document rulesDoc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(rulesXml)));
            Element rules = (Element) doc.importNode(rulesDoc.getDocumentElement(), true);
            root.appendChild(rules);

            return doc;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot build case DOM: " + e.getMessage(), e);
        }
    }

    private static void append(Document doc, Element parent, String name, String value) {
        Element e = doc.createElement(name);
        e.setTextContent(value == null ? "" : value);
        parent.appendChild(e);
    }
}
