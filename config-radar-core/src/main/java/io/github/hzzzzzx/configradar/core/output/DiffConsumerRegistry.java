package io.github.hzzzzzx.configradar.core.output;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/**
 * Explicit registry of {@link DiffConsumer} instances, looked up by id.
 *
 * <p>This is deliberately a plain in-process registry — no {@link java.util.ServiceLoader}, no
 * plugin classpath loading (the CLI does ServiceLoader discovery itself, then registers what it
 * finds). It mirrors {@link ConsumerRegistry} so diff and inventory consumers share the same
 * lookup ergonomics without sharing state: a diff consumer registered here is invisible to the
 * inventory registry, and vice versa.
 */
public final class DiffConsumerRegistry {

    private final java.util.Map<String, DiffConsumer> consumers = new LinkedHashMap<>();

    /** Registers a consumer; a later registration with the same id overwrites an earlier one. */
    public DiffConsumerRegistry register(DiffConsumer consumer) {
        consumers.put(consumer.id(), consumer);
        return this;
    }

    /** Looks up a consumer by id. */
    public Optional<DiffConsumer> find(String id) {
        return Optional.ofNullable(consumers.get(id));
    }

    /** All registered ids, in registration order. */
    public List<String> ids() {
        return List.copyOf(consumers.keySet());
    }
}
