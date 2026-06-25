package io.github.hzzzzzx.configradar.core.export;

import io.github.hzzzzzx.configradar.core.output.ConsumerContext;
import io.github.hzzzzzx.configradar.core.output.ConsumerSink;
import io.github.hzzzzzx.configradar.core.rule.ConfigRules;
import io.github.hzzzzzx.configradar.core.scan.FixturePaths;
import io.github.hzzzzzx.configradar.core.scan.ScanInput;
import io.github.hzzzzzx.configradar.core.scan.ScanOptions;
import io.github.hzzzzzx.configradar.core.scan.ScanPipeline;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HtmlReportConsumerTest {

    @Test
    void escapesUntrustedTextSoItCannotBreakOutOfTheDocument() {
        var escaped = HtmlReportConsumer.esc("<script>alert('x')</script> a&b \"q\"");
        assertFalse(escaped.contains("<script>"), "must not emit a raw script tag");
        assertTrue(escaped.contains("&lt;script&gt;"));
        assertTrue(escaped.contains("&amp;"));
        assertTrue(escaped.contains("&quot;"));
        assertTrue(escaped.contains("&#39;"));
    }

    @Test
    void rendersSelfContainedReportFromScan() throws Exception {
        var result = ScanPipeline.defaults().scan(
            ScanInput.of(FixturePaths.springBasic()),
            ScanOptions.defaults(),
            ConfigRules.empty()
        );
        var sink = new MemorySink();

        new HtmlReportConsumer().consume(result.inventory(), ConsumerContext.empty(), sink);

        var html = sink.contents.get("config-report.html").toString(StandardCharsets.UTF_8);
        assertTrue(html.startsWith("<!DOCTYPE html>"));
        assertTrue(html.contains("<style>"), "CSS is inlined — no external dependency");
        assertTrue(html.contains("filterRows"), "search filter is inlined");
        assertTrue(html.contains("<dialog id=\"modal\""), "native modal present");
        assertTrue(html.contains("conic-gradient("), "CSS donut chart present");
        assertTrue(html.contains("目录 / 模块分布"), "directory distribution section present");
        assertTrue(html.contains("数据从哪里来"), "report explains the scanner to inventory flow");
        assertTrue(html.contains("ConfigFinding(role=READ, defaultValue=3000)"));
        assertFalse(html.contains("<link "), "no external stylesheet");
        assertFalse(html.contains("<script src"), "no external script");
        assertTrue(html.contains("spring.application.name"));
    }

    static final class MemorySink implements ConsumerSink {
        final Map<String, ByteArrayOutputStream> contents = new LinkedHashMap<>();

        @Override
        public OutputStream openFile(String fileName) {
            var bytes = new ByteArrayOutputStream();
            contents.put(fileName, bytes);
            return bytes;
        }
    }
}
