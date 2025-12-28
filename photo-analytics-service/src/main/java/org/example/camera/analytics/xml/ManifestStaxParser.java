package org.example.camera.analytics.xml;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.Location;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ManifestStaxParser {

    public static class ManifestPhoto {
        public String fileKey;
        public String takenAt;
        public double latitude;
        public double longitude;
        public double altitude;
    }

    public static class Manifest {
        public String droneId;
        public String operatorId;
        public String startTime;
        public String endTime;
        public List<ManifestPhoto> photos = new ArrayList<>();
    }

    public Manifest parse(InputStream xml) {
        if (xml == null) {
            throw new IllegalArgumentException("Manifest StAX parse error: xml is null");
        }

        XMLStreamReader r = null;
        try {
            XMLInputFactory f = XMLInputFactory.newFactory();

            // DOCTYPE допускаем (для ЛР1), но внешние сущности/внешний DTD не тянем
            try { f.setProperty(XMLInputFactory.SUPPORT_DTD, true); } catch (Exception ignored) {}
            try { f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false); } catch (Exception ignored) {}
            try { f.setProperty("http://java.sun.com/xml/stream/properties/ignore-external-dtd", true); } catch (Exception ignored) {}

            r = f.createXMLStreamReader(xml);

            Manifest m = new Manifest();
            ManifestPhoto current = null;
            String tag = null;

            while (r.hasNext()) {
                int e = r.next();

                if (e == XMLStreamConstants.START_ELEMENT) {
                    tag = r.getLocalName();
                    if ("photo".equals(tag)) current = new ManifestPhoto();
                    continue;
                }

                if (e == XMLStreamConstants.CHARACTERS) {
                    if (tag == null) continue;
                    String text = r.getText();
                    if (text == null) continue;
                    text = text.trim();
                    if (text.isEmpty()) continue;

                    switch (tag) {
                        case "droneId": m.droneId = text; break;
                        case "operatorId": m.operatorId = text; break;
                        case "startTime": m.startTime = text; break;
                        case "endTime": m.endTime = text; break;

                        case "fileKey":
                            if (current != null) current.fileKey = text;
                            break;
                        case "takenAt":
                            if (current != null) current.takenAt = text;
                            break;
                        case "latitude":
                            if (current != null) current.latitude = parseDouble(text, "latitude", r.getLocation());
                            break;
                        case "longitude":
                            if (current != null) current.longitude = parseDouble(text, "longitude", r.getLocation());
                            break;
                        case "altitude":
                            if (current != null) current.altitude = parseDouble(text, "altitude", r.getLocation());
                            break;
                    }
                    continue;
                }

                if (e == XMLStreamConstants.END_ELEMENT) {
                    String end = r.getLocalName();
                    if ("photo".equals(end) && current != null) {
                        m.photos.add(current);
                        current = null;
                    }
                    tag = null;
                }
            }

            return m;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Manifest StAX parse error: " + ex.getMessage(), ex);
        } finally {
            if (r != null) {
                try { r.close(); } catch (Exception ignored) {}
            }
        }
    }

    private static double parseDouble(String text, String field, Location loc) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            String at = (loc == null) ? "" : (" at line=" + loc.getLineNumber() + ", col=" + loc.getColumnNumber());
            throw new IllegalArgumentException("Invalid number for '" + field + "': '" + text + "'" + at, e);
        }
    }
}
