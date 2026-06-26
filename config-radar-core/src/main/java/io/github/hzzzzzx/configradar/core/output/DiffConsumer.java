package io.github.hzzzzzx.configradar.core.output;

import io.github.hzzzzzx.configradar.core.model.ConfigDiff;

/**
 * Consumes a {@link ConfigDiff} (the difference between two inventories) and writes downstream
 * artifacts. This is the diff-side counterpart to {@link InventoryConsumer}: both reuse the same
 * {@link ConsumerContext} and {@link ConsumerSink}, keeping diff and inventory outputs on equal
 * footing while staying fully isolated (a diff consumer never receives an inventory, and vice
 * versa).
 *
 * <p>Implementations decide their own output shape and how many files they emit via the sink.
 */
public interface DiffConsumer {

    /**
     * Stable identifier for this consumer (used as the {@code --consumer} value on the CLI).
     * Must be unique within the diff-consumer registry.
     */
    String id();

    /**
     * Renders the diff into one or more files via {@code sink.openFile(...)}.
     *
     * @param diff    the diff to render
     * @param context downstream hints (profile/region/namespace and arbitrary properties)
     * @param sink    destination for output files (relative file names resolved by the sink)
     */
    void consume(ConfigDiff diff, ConsumerContext context, ConsumerSink sink) throws Exception;
}
