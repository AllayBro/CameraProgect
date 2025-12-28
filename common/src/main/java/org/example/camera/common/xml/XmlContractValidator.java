package org.example.camera.common.xml;

import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

public final class XmlContractValidator {

    private XmlContractValidator() {}

    public static void validateManifest(byte[] xmlBytes) {
        validateAgainstXsd(xmlBytes, "contracts/manifest/manifest.xsd");
    }

    public static void validateRules(byte[] xmlBytes) {
        validateAgainstXsd(xmlBytes, "contracts/rules/rules.xsd");
    }

    public static void validateCatalog(byte[] xmlBytes) {
        validateAgainstXsd(xmlBytes, "contracts/catalog/catalog.xsd");
    }

    private static void validateAgainstXsd(byte[] xmlBytes, String classpathXsd) {
        if (xmlBytes == null || xmlBytes.length == 0) {
            throw new XmlValidationException("XML is empty");
        }

        Schema schema = loadSchemaFromClasspath(classpathXsd);

        try {
            Validator validator = schema.newValidator();
            FirstErrorHandler handler = new FirstErrorHandler();
            validator.setErrorHandler(handler);

            // Контролируем парсер (чтобы DOCTYPE не тянул внешние DTD/ENTITY)
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            trySetFeature(spf, "http://xml.org/sax/features/external-general-entities", false);
            trySetFeature(spf, "http://xml.org/sax/features/external-parameter-entities", false);
            trySetFeature(spf, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            InputSource input = new InputSource(new ByteArrayInputStream(xmlBytes));
            validator.validate(new javax.xml.transform.sax.SAXSource(spf.newSAXParser().getXMLReader(), input));

            if (handler.firstError != null) {
                throw handler.toException();
            }
        } catch (XmlValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new XmlValidationException("XSD validation failed: " + e.getMessage(), e);
        }
    }

    private static Schema loadSchemaFromClasspath(String classpathXsd) {
        try (InputStream is = XmlContractValidator.class.getClassLoader().getResourceAsStream(classpathXsd)) {
            if (is == null) {
                throw new XmlValidationException("XSD not found on classpath: " + classpathXsd);
            }

            SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            try {
                sf.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                sf.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            } catch (Exception ignored) {
                // некоторые реализации не поддерживают свойства — не критично
            }

            return sf.newSchema(new StreamSource(is));
        } catch (XmlValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new XmlValidationException("Cannot load XSD: " + classpathXsd + ": " + e.getMessage(), e);
        }
    }

    private static void trySetFeature(SAXParserFactory spf, String feature, boolean value) {
        try {
            spf.setFeature(feature, value);
        } catch (Exception ignored) {
        }
    }

    private static class FirstErrorHandler implements ErrorHandler {
        SAXParseException firstError;

        @Override public void warning(SAXParseException exception) { if (firstError == null) firstError = exception; }
        @Override public void error(SAXParseException exception) { if (firstError == null) firstError = exception; }
        @Override public void fatalError(SAXParseException exception) { if (firstError == null) firstError = exception; }

        XmlValidationException toException() {
            return new XmlValidationException(
                    "Invalid XML by XSD: line=" + firstError.getLineNumber() +
                            ", col=" + firstError.getColumnNumber() +
                            ", msg=" + firstError.getMessage(),
                    firstError
            );
        }
    }
}
