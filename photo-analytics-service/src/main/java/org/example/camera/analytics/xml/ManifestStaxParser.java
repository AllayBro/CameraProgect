package org.example.camera.analytics.xml;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
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
        try {
            XMLStreamReader r = XMLInputFactory.newFactory().createXMLStreamReader(xml);
            Manifest m = new Manifest();
            ManifestPhoto current = null;
            String tag = null;

            while (r.hasNext()) {
                int e = r.next();
                if (e == XMLStreamConstants.START_ELEMENT) {
                    tag = r.getLocalName();
                    if ("photo".equals(tag)) current = new ManifestPhoto();
                } else if (e == XMLStreamConstants.CHARACTERS) {
                    String text = r.getText().trim();
                    if (text.isEmpty() || tag == null) continue;

                    switch (tag) {
                        case "droneId": m.droneId = text; break;
                        case "operatorId": m.operatorId = text; break;
                        case "startTime": m.startTime = text; break;
                        case "endTime": m.endTime = text; break;

                        case "fileKey": if (current != null) current.fileKey = text; break;
                        case "takenAt": if (current != null) current.takenAt = text; break;
                        case "latitude": if (current != null) current.latitude = Double.parseDouble(text); break;
                        case "longitude": if (current != null) current.longitude = Double.parseDouble(text); break;
                        case "altitude": if (current != null) current.altitude = Double.parseDouble(text); break;
                    }
                } else if (e == XMLStreamConstants.END_ELEMENT) {
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
        }
    }
}
