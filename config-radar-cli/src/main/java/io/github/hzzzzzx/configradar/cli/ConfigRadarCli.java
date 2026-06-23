package io.github.hzzzzzx.configradar.cli;

import io.github.hzzzzzx.configradar.core.diff.KeyBasedDiffStrategy;
import io.github.hzzzzzx.configradar.core.export.AppConfigCenterExporter;
import io.github.hzzzzzx.configradar.core.export.AppConfigEntry;
import io.github.hzzzzzx.configradar.core.io.YamlSupport;
import io.github.hzzzzzx.configradar.core.model.ConfigInventory;
import io.github.hzzzzzx.configradar.core.output.YamlInventoryConsumer;
import io.github.hzzzzzx.configradar.core.rule.RuleLoader;
import io.github.hzzzzzx.configradar.core.scan.EnvironmentHints;
import io.github.hzzzzzx.configradar.core.scan.RedactionPolicy;
import io.github.hzzzzzx.configradar.core.scan.ScanInput;
import io.github.hzzzzzx.configradar.core.scan.ScanOptions;
import io.github.hzzzzzx.configradar.core.scan.ScanPipeline;
import io.github.hzzzzzx.configradar.core.scan.SensitiveValueRedactionEnricher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "config-radar",
    mixinStandardHelpOptions = true,
    subcommands = {
        ConfigRadarCli.InventoryCommand.class,
        ConfigRadarCli.DiffCommand.class,
        ConfigRadarCli.ExportCommand.class
    }
)
public final class ConfigRadarCli implements Runnable {
    /**
     * CLI entry point.
     *
     * @param args command-line arguments passed to picocli
     */
    public static void main(String[] args) {
        System.exit(new CommandLine(new ConfigRadarCli()).execute(args));
    }

    /**
     * Prints root command usage when no subcommand is selected.
     */
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    @Command(name = "inventory", description = "Generate a configuration inventory.")
    static final class InventoryCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Project root to scan.")
        private Path projectRoot;

        @Option(names = {"-o", "--output"}, required = true, description = "Inventory YAML output path.")
        private Path output;

        @Option(names = "--rules", description = "Local config-radar-rules.yaml path.")
        private Path rulesFile;

        @Option(names = "--include-tests", description = "Include test sources. Off by default.")
        private boolean includeTests;

        @Option(names = "--include", description = "Path prefix to include. Can be repeated.")
        private List<Path> includePaths = List.of();

        @Option(names = "--exclude", description = "Path prefix to exclude. Can be repeated.")
        private List<Path> excludePaths = List.of();

        @Option(names = "--metrics", description = "Optional metrics/diagnostics sidecar YAML.")
        private Path metrics;

        @Option(names = "--enable-codegraph", description = "Use optional codegraph semantic detector when available.")
        private boolean enableCodegraph;

        @Option(names = "--parallelism", description = "Detector worker count. Defaults to CPU count capped at 8.")
        private int parallelism;

        @Option(names = "--profile", description = "Default profile hint for findings without a profile.")
        private String profile;

        @Option(names = "--region", description = "Default region hint for findings without a region.")
        private String region;

        @Option(names = "--namespace", description = "Default namespace hint for findings without a namespace.")
        private String namespace;

        @Option(names = "--redact-sensitive", description = "Mask values for sensitive-looking keys.")
        private boolean redactSensitive;

        /**
         * Runs the inventory skeleton: build input, load rules, scan, then write YAML outputs.
         *
         * @return process exit code
         * @throws Exception when input loading, scanning, or output writing fails
         */
        @Override
        public Integer call() throws Exception {
            if (!Files.isDirectory(projectRoot)) {
                return fail("Project root does not exist or is not a directory: " + projectRoot);
            }
            if (rulesFile != null && !Files.isReadable(rulesFile)) {
                return fail("Rules file does not exist or is not readable: " + rulesFile);
            }
            var resolvedRulesFile = resolveRulesFile(projectRoot, rulesFile);
            var input = new ScanInput(
                projectRoot,
                includePaths,
                excludePaths,
                null,
                new EnvironmentHints(profile, region, namespace),
                null
            );
            if (resolvedRulesFile != null) {
                input = new ScanInput(
                    input.projectRoot(),
                    input.includePaths(),
                    input.excludePaths(),
                    resolvedRulesFile,
                    input.environmentHints(),
                    input.buildHints()
                );
            }
            var options = new ScanOptions(
                includeTests,
                true,
                parallelism,
                0,
                null,
                redactSensitive ? RedactionPolicy.redactSensitive() : RedactionPolicy.disabled()
            );
            var rules = new RuleLoader().load(resolvedRulesFile);
            var result = ScanPipeline.defaults(enableCodegraph).scan(input, options, rules);
            writeParent(output);
            try (var out = Files.newOutputStream(output)) {
                new YamlInventoryConsumer().write(result.inventory(), out);
            }
            if (metrics != null) {
                writeParent(metrics);
                YamlSupport.mapper().writeValue(metrics.toFile(), result.report());
            }
            return 0;
        }

        private static Path resolveRulesFile(Path projectRoot, Path explicitRulesFile) {
            if (explicitRulesFile != null) {
                return explicitRulesFile;
            }
            var defaultRules = projectRoot.resolve("config-radar-rules.yaml");
            return Files.exists(defaultRules) ? defaultRules : null;
        }
    }

    @Command(name = "diff", description = "Compare two inventory YAML files.")
    static final class DiffCommand implements Callable<Integer> {
        @Option(names = "--base", required = true, description = "Base inventory YAML.")
        private Path base;

        @Option(names = "--head", required = true, description = "Head inventory YAML.")
        private Path head;

        @Option(names = {"-o", "--output"}, required = true, description = "Diff YAML output path.")
        private Path output;

        @Option(names = "--redact-sensitive", description = "Mask sensitive-looking values before diffing.")
        private boolean redactSensitive;

        /**
         * Runs the diff command shell by loading two inventories and writing a diff artifact.
         *
         * @return process exit code
         * @throws Exception when inventories cannot be read or the output cannot be written
         */
        @Override
        public Integer call() throws Exception {
            if (!Files.isReadable(base)) {
                return fail("Base inventory does not exist or is not readable: " + base);
            }
            if (!Files.isReadable(head)) {
                return fail("Head inventory does not exist or is not readable: " + head);
            }
            var mapper = YamlSupport.mapper();
            var baseInventory = mapper.readValue(base.toFile(), ConfigInventory.class);
            var headInventory = mapper.readValue(head.toFile(), ConfigInventory.class);
            var diff = new KeyBasedDiffStrategy().diff(baseInventory, headInventory);
            if (redactSensitive) {
                var redactor = new SensitiveValueRedactionEnricher();
                diff = redactor.redact(diff, RedactionPolicy.redactSensitive());
            }
            writeParent(output);
            mapper.writeValue(output.toFile(), diff);
            return 0;
        }
    }

    @Command(name = "export", description = "Export an inventory to the app-config-center format.")
    static final class ExportCommand implements Callable<Integer> {
        @Option(names = "--inventory", required = true, description = "Inventory YAML to convert.")
        private Path inventoryPath;

        @Option(names = {"-o", "--output"}, required = true, description = "App-config YAML output path.")
        private Path output;

        @Option(names = "--missing", description = "Optional output for keys missing a value (to fill in and merge back).")
        private Path missing;

        @Option(names = "--merge", description = "Optional filled missing-file to merge into the export.")
        private Path merge;

        /**
         * Reads an inventory, converts it to the app-config-center {@code app_configs} format, and
         * optionally writes a missing-value list or merges a filled one back in.
         *
         * @return process exit code
         * @throws Exception when the inventory cannot be read or output cannot be written
         */
        @Override
        public Integer call() throws Exception {
            if (!Files.isReadable(inventoryPath)) {
                return fail("Inventory does not exist or is not readable: " + inventoryPath);
            }
            var mapper = YamlSupport.mapper();
            var inventory = mapper.readValue(inventoryPath.toFile(), ConfigInventory.class);
            var exporter = new AppConfigCenterExporter();
            var result = exporter.export(inventory);

            var entries = result.entries();
            if (merge != null) {
                if (!Files.isReadable(merge)) {
                    return fail("Merge file does not exist or is not readable: " + merge);
                }
                // Merge files share the {"app_configs": [...]} wrapper, so unwrap before merging.
                var wrapped = mapper.readValue(merge.toFile(),
                    mapper.getTypeFactory().constructMapType(java.util.Map.class, String.class, Object.class));
                Object rawList = wrapped instanceof java.util.Map<?, ?> m ? m.get("app_configs") : wrapped;
                var listType = mapper.getTypeFactory()
                    .constructCollectionType(java.util.List.class, AppConfigEntry.class);
                java.util.List<AppConfigEntry> filled = mapper.convertValue(rawList, listType);
                entries = exporter.merge(entries, filled);
            }

            writeParent(output);
            mapper.writeValue(output.toFile(), java.util.Map.of("app_configs", entries));
            if (missing != null) {
                writeParent(missing);
                mapper.writeValue(missing.toFile(), java.util.Map.of("app_configs", result.missing()));
            }
            return 0;
        }
    }

    /**
     * Prints a clear error message to standard error and returns a non-zero exit code,
     * instead of letting picocli surface a raw stack trace to the user.
     *
     * @param message human-readable error description
     * @return non-zero exit code
     */
    private static int fail(String message) {
        System.err.println("config-radar: " + message);
        return 2;
    }

    /**
     * Creates the output parent directory when the user writes to a nested path.
     *
     * @param output output file path
     * @throws IOException when the parent directory cannot be created
     */
    private static void writeParent(Path output) throws IOException {
        var parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}
