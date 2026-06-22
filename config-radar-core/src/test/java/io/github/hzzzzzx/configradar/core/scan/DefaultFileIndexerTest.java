package io.github.hzzzzzx.configradar.core.scan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DefaultFileIndexerTest {
    @Test
    void indexesMainFilesAndSkipsTestsByDefault() throws Exception {
        var index = new DefaultFileIndexer().index(
            ScanInput.of(FixturePaths.springBasic()),
            ScanOptions.defaults()
        );

        assertTrue(index.ofType(FileType.YAML).stream()
            .anyMatch(file -> file.path().endsWith("src/main/resources/application.yml")));
        assertTrue(index.ofType(FileType.PROPERTIES).stream()
            .anyMatch(file -> file.path().endsWith("src/main/resources/application-dev.properties")));
        assertTrue(index.files().stream()
            .anyMatch(file -> file.path().endsWith(".env") && file.type() == FileType.OTHER));
        assertFalse(index.files().stream()
            .anyMatch(file -> file.path().toString().contains("src/test/")));
    }

    @Test
    void includesTestsWhenRequested() throws Exception {
        var index = new DefaultFileIndexer().index(
            ScanInput.of(FixturePaths.springBasic()),
            new ScanOptions(true, true, 0, 0, null)
        );

        assertTrue(index.files().stream()
            .anyMatch(file -> file.path().endsWith("src/test/resources/application-test.yml")));
    }

    @Test
    void rejectsMissingProjectRoot() {
        assertThrows(Exception.class, () -> new DefaultFileIndexer().index(
            ScanInput.of(FixturePaths.springBasic().resolve("missing")),
            ScanOptions.defaults()
        ));
    }
}
