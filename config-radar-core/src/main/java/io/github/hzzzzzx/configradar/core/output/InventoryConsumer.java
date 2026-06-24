package io.github.hzzzzzx.configradar.core.output;

import io.github.hzzzzzx.configradar.core.model.ConfigInventory;

/**
 * Consumes a {@link ConfigInventory} and writes one or more output files in a downstream format.
 *
 * <p>This is the downstream extension point: ConfigRadar (upstream) owns detection and produces the
 * typed {@code ConfigInventory}; a consumer (downstream) owns how that inventory is shaped into a
 * target format — field mapping, partitioning, template, deploy-time metadata. ConfigRadar does not
 * dictate the output; it only provides the inventory, a {@link ConsumerContext} carrying scan
 * environment + downstream-supplied properties, and a {@link ConsumerSink} to write files into.
 *
 * <p>Reference implementations live in this package ({@link YamlInventoryConsumer}) and in
 * {@code core.export} ({@code DefaultFormatConsumer}, {@code XacConsumer}). A downstream team
 * implements this interface and registers an instance via {@link ConsumerRegistry}.
 */
public interface InventoryConsumer {
    /**
     * Consumer id used for CLI {@code --consumer <id>} selection and registry lookup.
     *
     * @return unique id such as {@code "yaml"} or {@code "xac"}
     */
    String id();

    /**
     * Consumes the inventory, writing output file(s) into the sink using the supplied context.
     *
     * <p>The context merges ConfigRadar's scan environment (profile/region/namespace) with any
     * downstream-supplied properties; the consumer interprets them as it sees fit. A consumer may
     * write multiple files via {@code sink.openFile(...)} (e.g. a main list plus a secrets section).
     *
     * @param inventory the ConfigRadar inventory to consume
     * @param context   merged upstream + downstream context
     * @param sink      destination for output files
     * @throws Exception when consumption or writing fails
     */
    void consume(ConfigInventory inventory, ConsumerContext context, ConsumerSink sink) throws Exception;
}
