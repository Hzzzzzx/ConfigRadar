package io.github.hzzzzzx.configradar.core.output;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * File-based {@link ConsumerSink} that resolves relative file names against a base directory.
 *
 * <p>Parent directories are created as needed. Each {@link #openFile(String)} call opens a fresh
 * stream that the caller must close.
 */
public final class DirectoryConsumerSink implements ConsumerSink {
    private final Path baseDirectory;

    public DirectoryConsumerSink(Path baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    @Override
    public OutputStream openFile(String fileName) throws IOException {
        var resolved = baseDirectory.resolve(fileName);
        var parent = resolved.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return Files.newOutputStream(resolved);
    }

    /** Resolves a relative file name against the base directory. */
    public Path resolve(String fileName) {
        return baseDirectory.resolve(fileName);
    }
}
