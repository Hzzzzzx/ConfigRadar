package io.github.hzzzzzx.configradar.core.scan;

import java.nio.file.Files;
import java.nio.file.Path;

public final class FixturePaths {
    private FixturePaths() {
    }

    public static Path springBasic() {
        var root = Path.of("fixtures/spring-basic");
        if (Files.exists(root)) {
            return root;
        }
        return Path.of("../fixtures/spring-basic");
    }
}
