package org.example.camera.penalty.rest;

import org.springframework.web.bind.annotation.*;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;

@RestController
@RequestMapping("/api/penalty")
public class PenaltyController {

    @PostMapping("/check")
    public String check(@RequestBody PenaltyCheckRequest req) {
        // реальная логика (не заглушка): простое правило скорости
        boolean violation = req.speed > 60.0;

        String evidenceXml = buildEvidenceXml(req, violation);

        if (!violation) return "OK: no violation; evidence=" + evidenceXml;
        return "OK: SPEEDING; evidence=" + evidenceXml;
    }

    private String buildEvidenceXml(PenaltyCheckRequest req, boolean violation) {
        try {
            var dbf = DocumentBuilderFactory.newInstance();
            var doc = dbf.newDocumentBuilder().newDocument();

            var root = doc.createElement("evidence");
            root.setAttribute("recordId", req.recordId);
            root.setAttribute("violation", String.valueOf(violation));
            doc.appendChild(root);

            var speed = doc.createElement("speed");
            speed.setTextContent(String.valueOf(req.speed));
            root.appendChild(speed);

            var loc = doc.createElement("location");
            loc.setTextContent(req.location);
            root.appendChild(loc);

            var time = doc.createElement("time");
            time.setTextContent(req.time);
            root.appendChild(time);

            var conf = doc.createElement("confidence");
            conf.setTextContent(String.valueOf(req.confidence));
            root.appendChild(conf);

            var tf = TransformerFactory.newInstance().newTransformer();
            var sw = new StringWriter();
            tf.transform(new DOMSource(doc), new StreamResult(sw));
            return sw.toString();
        } catch (Exception e) {
            throw new IllegalStateException("DOM build evidence error: " + e.getMessage(), e);
        }
    }

    public static class PenaltyCheckRequest {
        public String recordId;
        public double speed;
        public String location;
        public String time;
        public double confidence;
    }
}
