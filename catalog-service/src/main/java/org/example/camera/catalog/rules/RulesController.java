package org.example.camera.catalog.rules;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/catalog/rules")
public class RulesController {

    private static final String RULES_XSD = "contracts/rules/rules.xsd";
    private static final String ACTIVE_ID = "active";

    private final RuleSetRepository repo;

    public RulesController(RuleSetRepository repo) {
        this.repo = repo;
    }

    // загрузка rules.xml (application/xml)
    @PostMapping(consumes = "application/xml")
    public String upload(@RequestBody byte[] xmlBytes) {
        if (xmlBytes == null || xmlBytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rules xml is empty");
        }

        validateRulesXsd(xmlBytes);

        String xml = new String(xmlBytes, StandardCharsets.UTF_8);
        String sha = sha256Hex(xmlBytes);
        Instant now = Instant.now();

        RuleSetEntity e = repo.findById(ACTIVE_ID).orElseGet(() -> {
            RuleSetEntity x = new RuleSetEntity(ACTIVE_ID);
            x.setCreatedAt(now);
            return x;
        });

        e.setUpdatedAt(now);
        e.setXml(xml);
        e.setSha256(sha);
        repo.save(e);

        return "OK sha256=" + sha;
    }

    // получить активные правила
    @GetMapping(value = "", produces = "application/xml")
    public String getActiveXml() {
        RuleSetEntity e = repo.findById(ACTIVE_ID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "rules not uploaded"));
        return e.getXml();
    }

    // XPath по rules.xml (возвращает текст найденных узлов)
    @GetMapping("/search")
    public List<String> search(@RequestParam("xpath") String xpathExpr) {
        if (xpathExpr == null || xpathExpr.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "xpath is required");
        }

        RuleSetEntity e = repo.findById(ACTIVE_ID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "rules not uploaded"));

        Document doc = parseXmlSecure(e.getXml());
        XPathExpression compiled = compileXpathOr400(xpathExpr);

        try {
            Object nodes = compiled.evaluate(doc, XPathConstants.NODESET);
            NodeList nl = (NodeList) nodes;

            List<String> out = new ArrayList<>();
            for (int i = 0; i < nl.getLength(); i++) {
                out.add(nl.item(i).getTextContent());
            }
            return out;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "XPath evaluation error: " + ex.getMessage(), ex);
        }
    }

    private static XPathExpression compileXpathOr400(String expr) {
        try {
            return XPathFactory.newInstance().newXPath().compile(expr);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid XPath: " + e.getMessage(), e);
        }
    }

    private void validateRulesXsd(byte[] xmlBytes) {
        try (var xsdStream = getClass().getClassLoader().getResourceAsStream(RULES_XSD)) {
            if (xsdStream == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "XSD not found: " + RULES_XSD);
            }

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

            var input = new InputSource(new ByteArrayInputStream(xmlBytes));
            v.validate(new SAXSource(spf.newSAXParser().getXMLReader(), input));
        } catch (SAXParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "rules.xml invalid by XSD: line=" + e.getLineNumber() + ", col=" + e.getColumnNumber() + ", msg=" + e.getMessage(), e);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rules.xml XSD validation failed: " + e.getMessage(), e);
        }
    }

    private static void trySetFeature(javax.xml.parsers.SAXParserFactory spf, String feature, boolean value) {
        try { spf.setFeature(feature, value); } catch (Exception ignored) {}
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

            return dbf.newDocumentBuilder().parse(new InputSource(new java.io.StringReader(xml)));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot parse rules.xml: " + e.getMessage(), e);
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
            throw new IllegalStateException("SHA-256 error: " + e.getMessage(), e);
        }
    }
}
