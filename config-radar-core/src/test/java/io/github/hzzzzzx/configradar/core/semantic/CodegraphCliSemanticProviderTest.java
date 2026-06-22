package io.github.hzzzzzx.configradar.core.semantic;

import io.github.hzzzzzx.configradar.core.rule.ConfigRules;
import io.github.hzzzzzx.configradar.core.scan.DefaultFileIndexer;
import io.github.hzzzzzx.configradar.core.scan.FixturePaths;
import io.github.hzzzzzx.configradar.core.scan.ScanContext;
import io.github.hzzzzzx.configradar.core.scan.ScanInput;
import io.github.hzzzzzx.configradar.core.scan.ScanOptions;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class CodegraphCliSemanticProviderTest {
    @TempDir
    private java.nio.file.Path tempDir;

    @Test
    @EnabledIf("toolsAvailable")
    void findsCustomMetaAnnotationUsagesFromCodegraphIndex() throws Exception {
        var project = tempDir.resolve("spring-basic");
        copyDirectory(FixturePaths.springBasic(), project);
        var input = ScanInput.of(project);
        var options = ScanOptions.defaults();
        var context = new ScanContext(
            input,
            options,
            ConfigRules.empty(),
            new DefaultFileIndexer().index(input, options)
        );

        var usages = new CodegraphCliSemanticProvider().findConfigUsages(context);

        assertTrue(usages.stream().anyMatch(usage ->
            usage.key().equals("payment.client.timeout")
                && usage.kind() == UsageKind.CUSTOM_ANNOTATION
                && usage.source().path().endsWith("PaymentClient.java")
        ));
    }

    static boolean toolsAvailable() {
        return commandWorks("codegraph", "--version") && commandWorks("sqlite3", "--version");
    }

    private static boolean commandWorks(String... command) {
        try {
            var process = new ProcessBuilder(command).start();
            return process.waitFor() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void copyDirectory(java.nio.file.Path source, java.nio.file.Path target) throws Exception {
        try (var paths = Files.walk(source)) {
            for (var path : paths.toList()) {
                var destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination);
                }
            }
        }
    }
}
