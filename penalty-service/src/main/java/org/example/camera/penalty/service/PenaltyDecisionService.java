package org.example.camera.penalty.service;

import org.example.camera.common.dto.PenaltyCheckRequestDto;
import org.example.camera.common.dto.PenaltyCheckResponseDto;
import org.example.camera.penalty.rules.RuleSetEntity;
import org.example.camera.penalty.rules.RuleSetRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;

@Service
public class PenaltyDecisionService {

    private static final String ACTIVE_ID = "active";

    private final RuleSetRepository rulesRepo;

    public PenaltyDecisionService(RuleSetRepository rulesRepo) {
        this.rulesRepo = rulesRepo;
    }

    public PenaltyCheckResponseDto decide(PenaltyCheckRequestDto req) {
        RuleSetEntity rules = rulesRepo.findById(ACTIVE_ID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "rules.xml is not uploaded to penalty-service"));

        Document rulesDoc = parseXmlSecure(rules.getXml());

        double speedLimit = evalNumber(rulesDoc, "number(/rules/speedLimitKmh)");
        double reviewThreshold = evalNumber(rulesDoc, "number(/rules/reviewConfidenceThreshold)");

        if (!Double.isFinite(speedLimit) || speedLimit <= 0) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid rules: speedLimitKmh");
        }
        if (!Double.isFinite(reviewThreshold) || reviewThreshold < 0 || reviewThreshold > 1) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid rules: reviewConfidenceThreshold");
        }

        double over = req.speed - speedLimit;

        PenaltyCheckResponseDto resp = new PenaltyCheckResponseDto();
        resp.recordId = req.recordId;

        if (over <= 0) {
            resp.ruleCode = "NO_VIOLATION";
            resp.amount = 0.0;
            resp.requiresReview = false;
            resp.decisionStatus = "NO_VIOLATION";
            return resp;
        }

        double amount = selectAmountForOver(rulesDoc, over);
        boolean requiresReview = req.confidence < reviewThreshold;

        resp.ruleCode = "SPEED_LIMIT";
        resp.amount = amount;
        resp.requiresReview = requiresReview;
        resp.decisionStatus = requiresReview ? "REQUIRES_REVIEW" : "APPROVED";
        return resp;
    }

    private static double selectAmountForOver(Document rulesDoc, double overKmh) {
        try {
            var xp = XPathFactory.newInstance().newXPath();

            // XPath отбирает кандидатов (DOM+XPath), финальная проверка интервала — по числам
            NodeList brackets = (NodeList) xp.evaluate("/rules/amounts/bracket", rulesDoc, XPathConstants.NODESET);
            if (brackets == null || brackets.getLength() == 0) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid rules: amounts/bracket is empty");
            }

            for (int i = 0; i < brackets.getLength(); i++) {
                var b = brackets.item(i);

                double from = toFinite(xp.evaluate("number(fromOverKmh)", b));
                String toRaw = xp.evaluate("string(toOverKmh)", b);
                Double to = (toRaw == null || toRaw.isBlank()) ? null : toFinite(toRaw);

                double amount = toFinite(xp.evaluate("number(amount)", b));

                boolean inRange = (overKmh >= from) && (to == null || overKmh < to);
                if (inRange) return amount;
            }

            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid rules: no bracket matched overKmh=" + overKmh);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Rules XPath error: " + e.getMessage(), e);
        }
    }

    private static double evalNumber(Document doc, String expr) {
        try {
            String s = XPathFactory.newInstance().newXPath().evaluate(expr, doc);
            return toFinite(s);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Rules XPath error: " + e.getMessage(), e);
        }
    }

    private static double toFinite(String s) {
        try {
            double v = Double.parseDouble(s);
            if (!Double.isFinite(v)) throw new NumberFormatException("not finite");
            return v;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid numeric in rules.xml: " + s, e);
        }
    }

    private static Document parseXmlSecure(String xml) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            try { dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true); } catch (Exception ignored) {}
            try { dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception ignored) {}
            try { dbf.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Exception ignored) {}
            try { dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Exception ignored) {}
            try { dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false); } catch (Exception ignored) {}

            return dbf.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot parse rules.xml: " + e.getMessage(), e);
        }
    }
}
