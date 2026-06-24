package io.github.hzzzzzx.configradar.core.output;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Sink an {@link InventoryConsumer} writes its output files into.
 *
 * <p>Supports multi-file output: a consumer may emit several files (e.g. a main list plus a
 * secrets section). Each call to {@link #openFile(String)} returns a fresh stream for that file
 * name; the caller owns closing it. The base directory is decided by the caller (the CLI uses the
 * {@code --output} parent).
 */
public interface ConsumerSink {
    /**
     * Opens a stream to write a named output file.
     *
     * <p>The file name is relative (e.g. {@code "app-configs.yaml"}); the sink resolves it against
     * its configured base directory. Parent directories are created as needed.
     *
     * @param fileName relative file name to write
     * @return output stream owned by the caller (must be closed)
     * @throws IOException when the file cannot be opened
     */
    OutputStream openFile(String fileName) throws IOException;
}
