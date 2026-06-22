package io.github.hzzzzzx.configradar.core.output;

import io.github.hzzzzzx.configradar.core.model.ConfigInventory;
import java.io.IOException;
import java.io.OutputStream;

/** Writes ConfigInventory to one output format. */
public interface InventoryConsumer {
    /**
     * Output format id.
     *
     * @return format id such as {@code yaml}
     */
    String id();

    /**
     * Writes an inventory to the target output stream.
     *
     * @param inventory inventory to serialize
     * @param output destination stream owned by the caller
     * @throws IOException when serialization or writing fails
     */
    void write(ConfigInventory inventory, OutputStream output) throws IOException;
}
