package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.ConfigValue;
import io.github.hzzzzzx.configradar.core.model.Confidence;
import io.github.hzzzzzx.configradar.core.model.EnvironmentContext;
import io.github.hzzzzzx.configradar.core.model.ExternalDetails;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.model.ScanFinding;
import io.github.hzzzzzx.configradar.core.model.SourceKind;
import io.github.hzzzzzx.configradar.core.model.SourceLocation;
import io.github.hzzzzzx.configradar.core.model.SpringPlaceholderDetails;
import io.github.hzzzzzx.configradar.core.model.ValueType;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/** Detects Spring-style placeholders in runtime XML resources. */
public final class LogbackSpringXmlDetector implements ConfigDetector {
    @Override
    public String id() {
        return "logback-spring-xml";
    }

    @Override
    public List<ScanFinding> detect(ScanContext context) throws Exception {
        var findings = new ArrayList<ScanFinding>();
        for (var file : context.fileIndex().ofType(FileType.XML)) {
            if (!isScannableXml(context, file)) {
                continue;
            }
            if (isLoggingXml(file)) {
                var factory = DocumentBuilderFactory.newInstance();
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                var document = factory.newDocumentBuilder().parse(file.path().toFile());
                var springProperties = document.getElementsByTagName("springProperty");
                for (var index = 0; index < springProperties.getLength(); index++) {
                    var element = (Element) springProperties.item(index);
                    var key = element.getAttribute("source");
                    if (!key.isBlank()) {
                        findings.add(new ConfigFinding(
                            key,
                            key,
                            FindingRole.READ,
                            null,
                            value(element.getAttribute("defaultValue")),
                            EnvironmentContext.none(),
                            source(context, file),
                            Confidence.HIGH,
                            id(),
                            new ExternalDetails("spring", "logback-spring-property", null)
                        ));
                    }
                }
            }
            addPlaceholders(context, file, Files.readString(file.path()), findings);
        }
        return findings;
    }

    private static boolean isScannableXml(ScanContext context, IndexedFile file) {
        if (isLoggingXml(file)) {
            return true;
        }
        var root = context.input().projectRoot();
        var path = root == null ? file.path() : root.toAbsolutePath().relativize(file.path().toAbsolutePath());
        return path.toString().contains("src/main/resources/");
    }

    private static boolean isLoggingXml(IndexedFile file) {
        var name = file.path().getFileName().toString();
        return name.equals("logback-spring.xml")
            || name.equals("logback.xml")
            || name.equals("log4j2-spring.xml")
            || name.equals("log4j2.xml");
    }

    private void addPlaceholders(ScanContext context, IndexedFile file, String text, List<ScanFinding> findings) {
        var start = text.indexOf("${");
        while (start >= 0) {
            var end = placeholderEnd(text, start);
            if (end < 0) {
                return;
            }
            var body = text.substring(start + 2, end);
            var split = placeholderSplit(body);
            var key = split.position() < 0 ? body : body.substring(0, split.position());
            var defaultValue = split.position() < 0 ? null : body.substring(split.position() + split.separatorLength());
            if (!key.isBlank()) {
                findings.add(new ConfigFinding(
                    key,
                    key,
                    FindingRole.READ,
                    null,
                    value(defaultValue),
                    EnvironmentContext.none(),
                    source(context, file),
                    Confidence.HIGH,
                    id(),
                    new SpringPlaceholderDetails(defaultValue, body)
                ));
            }
            if (defaultValue != null) {
                addPlaceholders(context, file, defaultValue, findings);
            }
            start = text.indexOf("${", end + 1);
        }
    }

    private static int placeholderEnd(String text, int start) {
        var depth = 0;
        for (var index = start; index < text.length(); index++) {
            if (index + 1 < text.length() && text.charAt(index) == '$' && text.charAt(index + 1) == '{') {
                depth++;
                index++;
                continue;
            }
            if (text.charAt(index) == '}') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static PlaceholderSplit placeholderSplit(String body) {
        var depth = 0;
        for (var index = 0; index < body.length(); index++) {
            if (index + 1 < body.length() && body.charAt(index) == '$' && body.charAt(index + 1) == '{') {
                depth++;
                index++;
                continue;
            }
            if (body.charAt(index) == '}') {
                depth--;
                continue;
            }
            if (depth == 0 && body.charAt(index) == ':') {
                var length = index + 1 < body.length() && body.charAt(index + 1) == '-' ? 2 : 1;
                return new PlaceholderSplit(index, length);
            }
        }
        return new PlaceholderSplit(-1, 0);
    }

    private record PlaceholderSplit(int position, int separatorLength) {
    }

    private static ConfigValue value(String raw) {
        return raw == null || raw.isBlank() ? null : new ConfigValue(raw, raw, typeOf(raw));
    }

    private static ValueType typeOf(String value) {
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return ValueType.BOOLEAN;
        }
        if (value.matches("-?\\d+")) {
            return ValueType.INTEGER;
        }
        return ValueType.STRING;
    }

    private static SourceLocation source(ScanContext context, IndexedFile file) {
        var root = context.input().projectRoot();
        var path = root == null ? file.path() : root.toAbsolutePath().relativize(file.path().toAbsolutePath());
        return new SourceLocation(path.toString(), null, null, SourceKind.XML, file.scope());
    }
}
