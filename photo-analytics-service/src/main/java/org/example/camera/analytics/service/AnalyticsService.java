package org.example.camera.analytics.service;

import org.example.camera.analytics.db.AnalysisResultEntity;
import org.example.camera.analytics.db.AnalysisResultRepository;
import org.example.camera.analytics.db.CaptureSessionEntity;
import org.example.camera.analytics.db.CaptureSessionRepository;
import org.example.camera.analytics.xml.ManifestStaxParser;
import org.example.camera.common.dto.AnalysisResultDto;
import org.example.camera.common.dto.CatalogImportRequestDto;
import org.example.camera.common.dto.ManifestDto;
import org.example.camera.common.dto.PhotoDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.xml.sax.*;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class AnalyticsService {

    private static final String MANIFEST_XSD = "contracts/manifest/manifest.xsd";
    private static final String MANIFEST_DTD = "contracts/manifest/manifest.dtd";

    private final CaptureSessionRepository sessions;
    private final AnalysisResultRepository resultsRepo;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ManifestStaxParser parser = new ManifestStaxParser();

    @Value("${app.catalog.base-url}")
    private String catalogBaseUrl;

    public AnalyticsService(CaptureSessionRepository sessions, AnalysisResultRepository resultsRepo) {
        this.sessions = sessions;
        this.resultsRepo = resultsRepo;
    }

    public CaptureSessionEntity startSession(String droneId, String operatorId) {
        CaptureSessionEntity s = new CaptureSessionEntity();
        s.setSessionId(UUID.randomUUID().toString());
        s.setDroneId(droneId);
        s.setOperatorId(operatorId);
        s.setStartTime(Instant.now());
        s.setStatus("CREATED");
        return sessions.save(s);
    }

    public CaptureSessionEntity getSession(String sessionId) {
        return sessions.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found: " + sessionId));
    }

    // Старый путь оставляем: принимает только manifest.xml
    public int submitManifest(String sessionId, InputStream manifestXml) {
        if (manifestXml == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "manifestXml is required");

        CaptureSessionEntity s = getSession(sessionId);

        byte[] xmlBytes = readAllBytesOr400(manifestXml, "cannot read manifest");

        // XSD обязательно
        validateManifestXsd(xmlBytes);

        // DTD валидируем только если DOCTYPE есть (чтобы не ломать старые файлы)
        if (containsDoctype(xmlBytes)) {
            validateManifestDtd(xmlBytes);
        }

        return processParsedManifest(sessionId, s, xmlBytes, null);
    }

    // Новый путь: ZIP пакет
    public PackageSubmitResult submitPackage(String sessionId, InputStream zipStream) {
        if (zipStream == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "package zip is required");

        CaptureSessionEntity s = getSession(sessionId);

        byte[] zipBytes = readAllBytesOr400(zipStream, "cannot read zip package");
        String checksum = sha256Hex(zipBytes);

        ZipExtract extract = extractPackage(zipBytes);

        if (extract.manifestXmlBytes == null || extract.manifestXmlBytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "manifest.xml not found in zip");
        }

        // Для пакетного сценария DOCTYPE обязателен (ЛР1)
        if (!containsDoctype(extract.manifestXmlBytes)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "manifest.xml must contain DOCTYPE for DTD validation (LR1)");
        }

        validateManifestXsd(extract.manifestXmlBytes);
        validateManifestDtd(extract.manifestXmlBytes);

        // checksum пакета сохраняем в сессию
        s.setPackageChecksum(checksum);
        s.setStatus("PACKAGE_VALIDATED");
        sessions.save(s);

        int photos = processParsedManifest(sessionId, s, extract.manifestXmlBytes, extract.entryNames);

        return new PackageSubmitResult(photos, checksum);
    }

    private int processParsedManifest(String sessionId, CaptureSessionEntity s, byte[] manifestXmlBytes, Set<String> zipEntries) {
        ManifestStaxParser.Manifest m = parser.parse(new ByteArrayInputStream(manifestXmlBytes));

        // целостность: данные из manifest должны совпасть с session (если заданы)
        if (m.droneId != null && s.getDroneId() != null && !m.droneId.equals(s.getDroneId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "droneId mismatch: session=" + s.getDroneId() + ", manifest=" + m.droneId);
        }
        if (m.operatorId != null && s.getOperatorId() != null && !m.operatorId.equals(s.getOperatorId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "operatorId mismatch: session=" + s.getOperatorId() + ", manifest=" + m.operatorId);
        }

        // если в manifest есть start/end, можно синхронизировать в entity (не ломает логику)
        applyTimesIfParsable(s, m);

        s.setEndTime(Instant.now());
        s.setStatus("MANIFEST_PARSED");
        sessions.save(s);

        // проверка “пакетности”: если пришёл ZIP — все fileKey должны существовать в zip
        if (zipEntries != null) {
            for (ManifestStaxParser.ManifestPhoto p : m.photos) {
                if (p == null || p.fileKey == null || p.fileKey.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "photo.fileKey is required");
                }
                if (!zipContains(zipEntries, p.fileKey)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "zip package missing file: " + p.fileKey);
                }
            }
        }

        // DTO manifest
        ManifestDto manifestDto = toManifestDto(m, s);

        // расчёт анализа (без заглушек)
        List<AnalysisResultDto> analysis = buildAnalysisResults(manifestDto);

        // сохранить анализ в БД (как данные, idempotent по recordId)
        saveAnalysisResults(sessionId, analysis);

        s.setStatus("ANALYSIS_SAVED");
        sessions.save(s);

        // отправить в catalog единым контрактом
        CatalogImportRequestDto req = new CatalogImportRequestDto();
        req.sessionId = s.getSessionId();
        req.manifest = manifestDto;
        req.analysisResults = analysis;

        try {
            restTemplate.postForEntity(
                    catalogBaseUrl + "/api/catalog/import",
                    req,
                    Void.class
            );
        } catch (RestClientException ex) {
            s.setStatus("CATALOG_CALL_FAILED");
            sessions.save(s);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Cannot call catalog-service: " + ex.getMessage(), ex);
        }

        s.setStatus("CATALOG_SENT");
        sessions.save(s);

        return (m.photos != null) ? m.photos.size() : 0;
    }

    private ManifestDto toManifestDto(ManifestStaxParser.Manifest m, CaptureSessionEntity s) {
        ManifestDto dto = new ManifestDto();

        // ожидаемые поля общего контракта (public fields)
        dto.droneId = m.droneId != null ? m.droneId : s.getDroneId();
        dto.operatorId = m.operatorId != null ? m.operatorId : s.getOperatorId();
        dto.startTime = m.startTime;
        dto.endTime = m.endTime;
        dto.packageChecksum = s.getPackageChecksum();

        if (m.photos != null) {
            for (ManifestStaxParser.ManifestPhoto p : m.photos) {
                PhotoDto pd = new PhotoDto();
                pd.fileKey = p.fileKey;
                pd.takenAt = p.takenAt;
                pd.latitude = p.latitude;
                pd.longitude = p.longitude;
                pd.altitude = p.altitude;
                dto.photos.add(pd);
            }
        }
        return dto;
    }

    private void saveAnalysisResults(String sessionId, List<AnalysisResultDto> list) {
        Instant now = Instant.now();

        for (AnalysisResultDto ar : list) {
            if (ar == null || ar.fileKey == null || ar.fileKey.isBlank()) continue;

            String recordId = sessionId + ":" + ar.fileKey;

            AnalysisResultEntity e = resultsRepo.findById(recordId).orElseGet(() -> {
                AnalysisResultEntity x = new AnalysisResultEntity(recordId);
                x.setCreatedAt(now);
                return x;
            });

            e.setUpdatedAt(now);
            e.setSessionId(sessionId);
            e.setFileKey(ar.fileKey);
            e.setDistanceMeters(ar.distance);
            e.setSpeedKmh(ar.speed);
            e.setConfidence(ar.confidence);
            e.setObjectType(ar.objectType);
            e.setModelVersion(ar.modelVersion);

            resultsRepo.save(e);
        }
    }

    private List<AnalysisResultDto> buildAnalysisResults(ManifestDto manifest) {
        List<AnalysisResultDto> out = new ArrayList<>();
        if (manifest == null || manifest.photos == null) return out;

        PhotoDto prev = null;
        Epoch prevT = null;

        for (PhotoDto cur : manifest.photos) {
            if (cur == null || cur.fileKey == null || cur.fileKey.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "photo.fileKey is required");
            }
            if (cur.takenAt == null || cur.takenAt.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "photo.takenAt is required for fileKey=" + cur.fileKey);
            }

            Epoch curT = parseTime(cur.takenAt);

            double distanceMeters = 0.0;
            double speedKmh = 0.0;

            if (prev != null && prevT != null && curT != null) {
                long dtMillis = curT.ms - prevT.ms;
                if (dtMillis > 0) {
                    distanceMeters = haversineMeters(prev.latitude, prev.longitude, cur.latitude, cur.longitude);
                    double speedMps = distanceMeters / (dtMillis / 1000.0);
                    speedKmh = speedMps * 3.6;
                }
            }

            AnalysisResultDto ar = new AnalysisResultDto();
            ar.fileKey = cur.fileKey;
            ar.distance = distanceMeters;
            ar.speed = speedKmh;

            int ok = 0;
            int total = 3;
            if (!cur.fileKey.isBlank()) ok++;
            if (curT != null) ok++;
            if (isValidLatLon(cur.latitude, cur.longitude)) ok++;
            ar.confidence = (double) ok / (double) total;

            // objectType/modelVersion не задаём, если реально не считаются
            out.add(ar);

            prev = cur;
            prevT = curT;
        }

        return out;
    }

    private static boolean isValidLatLon(double lat, double lon) {
        return lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0;
    }

    private static class Epoch {
        final long ms;
        Epoch(long ms) { this.ms = ms; }
    }

    private static Epoch parseTime(String s) {
        try {
            return new Epoch(Instant.parse(s).toEpochMilli());
        } catch (DateTimeParseException ignored) {
            try {
                return new Epoch(OffsetDateTime.parse(s).toInstant().toEpochMilli());
            } catch (DateTimeParseException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid takenAt dateTime: " + s, e);
            }
        }
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000.0;
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = Math.toRadians(lat2 - lat1);
        double dLambda = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private void validateManifestXsd(byte[] xmlBytes) {
        try (InputStream xsd = getClass().getClassLoader().getResourceAsStream(MANIFEST_XSD)) {
            if (xsd == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "XSD not found: " + MANIFEST_XSD);
            }

            SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            try {
                sf.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                sf.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            } catch (Exception ignored) {}

            Schema schema = sf.newSchema(new StreamSource(xsd));
            Validator validator = schema.newValidator();

            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);

            // Не грузим внешние сущности/DTD при XSD-валидации
            trySetFeature(spf, "http://xml.org/sax/features/external-general-entities", false);
            trySetFeature(spf, "http://xml.org/sax/features/external-parameter-entities", false);
            trySetFeature(spf, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            InputSource input = new InputSource(new ByteArrayInputStream(xmlBytes));
            SAXSource source = new SAXSource(spf.newSAXParser().getXMLReader(), input);

            validator.validate(source);
        } catch (SAXParseException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "manifest.xml invalid by XSD: line=" + e.getLineNumber() + ", col=" + e.getColumnNumber() + ", msg=" + e.getMessage(),
                    e
            );
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "manifest.xml XSD validation failed: " + e.getMessage(), e);
        }
    }

    private void validateManifestDtd(byte[] xmlBytes) {
        try (InputStream dtdStream = getClass().getClassLoader().getResourceAsStream(MANIFEST_DTD)) {
            if (dtdStream == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "DTD not found: " + MANIFEST_DTD);
            }

            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            spf.setValidating(true);

            XMLReader reader = spf.newSAXParser().getXMLReader();
            reader.setErrorHandler(new ErrorHandler() {
                @Override public void warning(SAXParseException exception) throws SAXException { throw exception; }
                @Override public void error(SAXParseException exception) throws SAXException { throw exception; }
                @Override public void fatalError(SAXParseException exception) throws SAXException { throw exception; }
            });

            reader.setEntityResolver((publicId, systemId) -> {
                // Разрешаем ТОЛЬКО наш manifest.dtd
                if (systemId != null && systemId.contains("manifest.dtd")) {
                    InputSource src = new InputSource(getClass().getClassLoader().getResourceAsStream(MANIFEST_DTD));
                    src.setPublicId(publicId);
                    src.setSystemId(systemId);
                    return src;
                }
                throw new SAXException("External entity is not allowed: " + systemId);
            });

            reader.parse(new InputSource(new ByteArrayInputStream(xmlBytes)));
        } catch (SAXParseException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "manifest.xml invalid by DTD: line=" + e.getLineNumber() + ", col=" + e.getColumnNumber() + ", msg=" + e.getMessage(),
                    e
            );
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "manifest.xml DTD validation failed: " + e.getMessage(), e);
        }
    }

    private static void trySetFeature(SAXParserFactory spf, String feature, boolean value) {
        try { spf.setFeature(feature, value); } catch (Exception ignored) {}
    }

    private static boolean containsDoctype(byte[] xmlBytes) {
        String s = new String(xmlBytes, StandardCharsets.UTF_8);
        return s.contains("<!DOCTYPE");
    }

    private static byte[] readAllBytesOr400(InputStream is, String message) {
        try {
            return is.readAllBytes();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message + ": " + e.getMessage(), e);
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

    private static class ZipExtract {
        byte[] manifestXmlBytes;
        Set<String> entryNames = new HashSet<>();
    }

    private static ZipExtract extractPackage(byte[] zipBytes) {
        ZipExtract z = new ZipExtract();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) continue;

                String name = e.getName();
                z.entryNames.add(name);

                if (name != null && name.toLowerCase(Locale.ROOT).endsWith("manifest.xml")) {
                    z.manifestXmlBytes = zis.readAllBytes();
                }
            }
            return z;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid zip: " + ex.getMessage(), ex);
        }
    }

    private static boolean zipContains(Set<String> entries, String fileKey) {
        if (entries.contains(fileKey)) return true;
        String suffix = "/" + fileKey;
        for (String e : entries) {
            if (e != null && e.endsWith(suffix)) return true;
        }
        return false;
    }

    private void applyTimesIfParsable(CaptureSessionEntity s, ManifestStaxParser.Manifest m) {
        if (m.startTime != null) {
            try { s.setStartTime(Instant.parse(m.startTime)); } catch (Exception ignored) {}
        }
        if (m.endTime != null) {
            try { s.setEndTime(Instant.parse(m.endTime)); } catch (Exception ignored) {}
        }
    }

    public static class PackageSubmitResult {
        public final int photos;
        public final String packageChecksum;

        public PackageSubmitResult(int photos, String packageChecksum) {
            this.photos = photos;
            this.packageChecksum = packageChecksum;
        }
    }
}
