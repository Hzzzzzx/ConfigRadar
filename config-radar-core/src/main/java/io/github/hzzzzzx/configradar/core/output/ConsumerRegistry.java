package io.github.hzzzzzx.configradar.core.output;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/**
 * Explicit registry of {@link InventoryConsumer} instances, looked up by id.
 *
 * <p>This is deliberately a plain in-process registry — no {@link java.util.ServiceLoader}, no
 * plugin classpath loading (see {@code technical-decisions.md}). The CLI registers built-in
 * consumers at startup; embedded callers register their own before scanning.
 */
public final class ConsumerRegistry {
    private final LinkedHashMap<String, InventoryConsumer> consumers = new LinkedHashMap<>();

    /** Registers a consumer, replacing any earlier one with the same id. */
    public ConsumerRegistry register(InventoryConsumer consumer) {
        consumers.put(consumer.id(), consumer);
        return this;
    }

    /** Returns the consumer for an id, or empty when none is registered. */
    public Optional<InventoryConsumer> find(String id) {
        return Optional.ofNullable(consumers.get(id));
    }

    /** Returns the ids of all registered consumers. */
    public List<String> ids() {
        return List.copyOf(consumers.keySet());
    }
}
