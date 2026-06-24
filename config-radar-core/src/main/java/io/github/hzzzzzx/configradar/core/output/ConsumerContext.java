package io.github.hzzzzzx.configradar.core.output;

import java.util.Map;

/**
 * Context passed to an {@link InventoryConsumer} at consumption time.
 *
 * <p>It merges ConfigRadar's own scan environment ({@code profile}/{@code region}/{@code namespace})
 * with arbitrary downstream-supplied key/value pairs ({@code properties}). ConfigRadar does not
 * interpret the downstream properties — it only carries them so a consumer can make its own
 * decisions (e.g. resolve {@code scope} from a module/environment mapping it owns).
 *
 * <p>This is the seam between the upstream (detection, which ConfigRadar owns) and the downstream
 * (consumption, which the consumer owns). See {@code docs/downstream-consumers.md}.
 *
 * @param profile    active profile discovered during scan, or the {@code --profile} hint
 * @param region     region hint, or null when not provided
 * @param namespace  namespace hint, or null when not provided
 * @param properties downstream-supplied key/value pairs; never null, empty when none provided
 */
public record ConsumerContext(
    String profile,
    String region,
    String namespace,
    Map<String, String> properties
) {
    /** Empty context with no profile and no downstream properties. */
    public static ConsumerContext empty() {
        return new ConsumerContext(null, null, null, Map.of());
    }

    /** Creates a context from scan profile/region/namespace with no downstream properties. */
    public static ConsumerContext of(String profile, String region, String namespace) {
        return new ConsumerContext(profile, region, namespace, Map.of());
    }

    /**
     * Returns the downstream property for a key, or null when absent.
     *
     * @param key downstream property key
     * @return value, or null
     */
    public String property(String key) {
        return properties.get(key);
    }

    public ConsumerContext {
        if (properties == null) {
            properties = Map.of();
        }
    }
}
