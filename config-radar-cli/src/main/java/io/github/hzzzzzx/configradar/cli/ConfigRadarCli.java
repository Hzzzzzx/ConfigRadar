package io.github.hzzzzzx.configradar.cli;

import io.github.hzzzzzx.configradar.core.diff.ConfigDiffFilter;
import io.github.hzzzzzx.configradar.core.diff.KeyBasedDiffStrategy;
import io.github.hzzzzzx.configradar.core.export.AppConfigCenterExporter;
import io.github.hzzzzzx.configradar.core.export.AppConfigEntry;
import io.github.hzzzzzx.configradar.core.export.DefaultFormatConsumer;
import io.github.hzzzzzx.configradar.core.export.HtmlReportConsumer;
import io.github.hzzzzzx.configradar.core.io.YamlSupport;
import io.github.hzzzzzx.configradar.core.model.ConfigDiff;
import io.github.hzzzzzx.configradar.core.model.ConfigInventory;
import io.github.hzzzzzx.configradar.core.output.ConsumerContext;
import io.github.hzzzzzx.configradar.core.output.ConsumerRegistry;
import io.github.hzzzzzx.configradar.core.output.DiffConsumer;
import io.github.hzzzzzx.configradar.core.output.DiffConsumerRegistry;
import io.github.hzzzzzx.configradar.core.output.DirectoryConsumerSink;
import io.github.hzzzzzx.configradar.core.output.InventoryConsumer;
import io.github.hzzzzzx.configradar.core.output.YamlDiffConsumer;
import io.github.hzzzzzx.configradar.core.output.YamlInventoryConsumer;
import io.github.hzzzzzx.configradar.core.rule.ConfigRules;
import io.github.hzzzzzx.configradar.core.rule.RuleLoader;
import io.github.hzzzzzx.configradar.core.scan.EnvironmentHints;
import io.github.hzzzzzx.configradar.core.scan.RedactionPolicy;
import io.github.hzzzzzx.configradar.core.scan.ScanInput;
import io.github.hzzzzzx.configradar.core.scan.ScanOptions;
import io.github.hzzzzzx.configradar.core.scan.ScanPipeline;
import io.github.hzzzzzx.configradar.core.scan.SensitiveValueRedactionEnricher;
import io.github.hzzzzzx.configradar.core.scm.GitClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
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
        ConfigRadarCli.ExportCommand.class,
        ConfigRadarCli.ConfigDiffCommand.class
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

        @Option(names = "--consumer", defaultValue = "yaml",
            description = "Consumer id (default: yaml). Built-in: yaml, default, html. Additional consumers (e.g. xac) are discovered from modules on the classpath.")
        private String consumerId;

        @Option(names = "-D", description = "Downstream context property (key=value, repeatable). Passed to the consumer, e.g. -D scope=prod.")
        private java.util.List<String> contextProps = java.util.List.of();

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
            // Route output through the selected consumer; ConfigRadar owns detection, the consumer
            // owns the output shape. yaml is the default (ConfigRadar's own inventory format).
            var consumer = builtinConsumers().find(consumerId);
            if (consumer.isEmpty()) {
                return fail("Unknown --consumer '" + consumerId + "'; available: " + builtinConsumers().ids());
            }
            writeParent(output);
            var context = new ConsumerContext(profile, region, namespace, parseContextProps(contextProps));
            // yaml writes the exact -o path (backward compatible); other consumers write into -o's
            // directory (they may emit multiple files, e.g. app-configs + J2C).
            if ("yaml".equals(consumerId)) {
                try (var out = Files.newOutputStream(output)) {
                    YamlSupport.mapper().writeValue(out, result.inventory());
                }
            } else {
                var sink = new DirectoryConsumerSink(output.getParent());
                consumer.get().consume(result.inventory(), context, sink);
            }
            if (metrics != null) {
                writeParent(metrics);
                YamlSupport.mapper().writeValue(metrics.toFile(), result.report());
            }
            return 0;
        }

        /**
         * Builds the consumer registry: core built-ins first, then any discovered via ServiceLoader
         * (e.g. the XAC module, which lives in its own Maven module and registers via
         * META-INF/services). The CLI itself never imports consumer modules, so downstream formats
         * evolve without touching the CLI.
         */
        private static ConsumerRegistry builtinConsumers() {
            var registry = new ConsumerRegistry()
                .register(new YamlInventoryConsumer())
                .register(new DefaultFormatConsumer())
                .register(new HtmlReportConsumer());
            // ServiceLoader discovers consumers packaged in the fat jar (e.g. config-radar-xac).
            for (var discovered : java.util.ServiceLoader.load(InventoryConsumer.class)) {
                registry.register(discovered);
            }
            return registry;
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

        @Option(names = {"-o", "--output"}, required = true,
            description = "Diff YAML output path (file in yaml mode; directory under --consumer).")
        private Path output;

        @Option(names = "--redact-sensitive", description = "Mask sensitive-looking values before diffing.")
        private boolean redactSensitive;

        @Option(names = "--profile", description = "Default profile hint, passed to consumers.")
        private String profile;

        @Option(names = "--region", description = "Default region hint, passed to consumers.")
        private String region;

        @Option(names = "--namespace", description = "Default namespace hint, passed to consumers.")
        private String namespace;

        @Option(names = "--consumer", defaultValue = "yaml",
            description = "Consumer id (default: yaml). Additional consumers (e.g. xac) are discovered from modules on the classpath.")
        private String consumerId;

        @Option(names = "-D", description = "Downstream context property (key=value, repeatable). Passed to the consumer.")
        private java.util.List<String> contextProps = java.util.List.of();

        /**
         * Loads two inventories, diffs them, and writes the result. With the default {@code yaml}
         * consumer {@code -o} is the exact output file; with another consumer {@code -o} is the
         * output directory (the diff YAML and consumer artifacts are written into it), mirroring
         * {@code config-diff}.
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

            if ("yaml".equals(consumerId)) {
                writeParent(output);
                mapper.writeValue(output.toFile(), diff);
            } else {
                if (Files.exists(output) && !Files.isDirectory(output)) {
                    return fail("--consumer requires -o to be a directory, but it is an existing file: " + output);
                }
                Files.createDirectories(output);
                var consumer = builtinDiffConsumers().find(consumerId);
                if (consumer.isEmpty()) {
                    return fail("Unknown --consumer '" + consumerId + "'; available: " + builtinDiffConsumers().ids());
                }
                mapper.writeValue(output.resolve("config-diff.yaml").toFile(), diff);
                var context = new ConsumerContext(profile, region, namespace, parseContextProps(contextProps));
                consumer.get().consume(diff, context, new DirectoryConsumerSink(output));
            }
            return 0;
        }
    }

    @Command(name = "export", description = "Export an inventory to the default app_configs format.")
    static final class ExportCommand implements Callable<Integer> {
        @Option(names = "--inventory", required = true, description = "Inventory YAML to convert.")
        private Path inventoryPath;

        @Option(names = {"-o", "--output"}, required = true, description = "Output YAML path.")
        private Path output;

        @Option(names = "--missing", description = "Optional output for keys missing a value (to fill in and merge back).")
        private Path missing;

        @Option(names = "--merge", description = "Optional filled missing-file to merge into the export.")
        private Path merge;

        /**
         * Reads an inventory, converts it to the default app_configs format, and optionally writes a
         * missing-value list or merges a filled one back in. For other formats (e.g. xac), use
         * {@code inventory --consumer <id>} instead.
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
            var outputMap = new java.util.LinkedHashMap<String, Object>();
            outputMap.put("app_configs", entries);
            mapper.writeValue(output.toFile(), outputMap);
            if (missing != null) {
                writeParent(missing);
                mapper.writeValue(missing.toFile(), java.util.Map.of("app_configs", result.missing()));
            }
            return 0;
        }
    }

    @Command(name = "config-diff",
        description = "Diff config between two git commits, scoped to changed files.")
    static final class ConfigDiffCommand implements Callable<Integer> {
        @Option(names = "--repo", description = "Git repository path (default: current directory).")
        private Path repo;

        @Option(names = "--base-ref", required = true, description = "Base commit-ish (tag/branch/sha).")
        private String baseRef;

        @Option(names = "--head-ref", required = true, description = "Head commit-ish (tag/branch/sha).")
        private String headRef;

        @Option(names = {"-o", "--output"}, required = true, description = "Diff YAML output path.")
        private Path output;

        @Option(names = "--profile", description = "Default profile hint (must match on both sides).")
        private String profile;

        @Option(names = "--region", description = "Default region hint.")
        private String region;

        @Option(names = "--namespace", description = "Default namespace hint.")
        private String namespace;

        @Option(names = "--redact-sensitive", description = "Mask sensitive-looking values.")
        private boolean redactSensitive;

        @Option(names = "--consumer", defaultValue = "yaml",
            description = "Consumer id (default: yaml). Built-in: yaml. Additional consumers (e.g. xac) are discovered from modules on the classpath.")
        private String consumerId;

        @Option(names = "-D", description = "Downstream context property (key=value, repeatable). Passed to the consumer.")
        private java.util.List<String> contextProps = java.util.List.of();

        /**
         * Materializes two commits into worktrees, scans each, diffs, and filters to only the
         * changes touching files that git reports as changed between the two refs.
         *
         * @return process exit code
         * @throws Exception when git, scanning, or writing fails
         */
        @Override
        public Integer call() throws Exception {
            var repository = repo != null ? repo : Path.of(".").toAbsolutePath();
            var git = new GitClient();
            var repoRoot = git.repositoryRoot(repository);
            if (repoRoot == null) {
                return fail("Not a git repository: " + repository);
            }

            var changedFiles = new HashSet<>(git.changedFiles(repoRoot, baseRef, headRef));
            if (changedFiles.isEmpty()) {
                System.err.println("config-radar: no changed files between " + baseRef + " and " + headRef);
            }

            Path baseWt = null;
            Path headWt = null;
            try {
                baseWt = Files.createTempDirectory("cr-cfgdiff-base");
                headWt = Files.createTempDirectory("cr-cfgdiff-head");
                System.err.println("config-radar: checking out " + baseRef + " ...");
                materializeCommit(git, repoRoot, baseRef, baseWt);
                System.err.println("config-radar: checking out " + headRef + " ...");
                materializeCommit(git, repoRoot, headRef, headWt);

                System.err.println("config-radar: scanning base " + baseRef + " ...");
                var baseInventory = scan(baseWt);
                System.err.println("config-radar: scanning head " + headRef + " ...");
                var headInventory = scan(headWt);

                System.err.println("config-radar: diffing " + baseRef + ".." + headRef + " ...");
                var diff = new KeyBasedDiffStrategy().diff(baseInventory, headInventory);
                var filtered = new ConfigDiffFilter().filter(diff, changedFiles, headInventory);

                var toWrite = redactSensitive
                    ? new SensitiveValueRedactionEnricher().redact(filtered, RedactionPolicy.redactSensitive())
                    : filtered;

                if ("yaml".equals(consumerId)) {
                    // yaml-only mode: -o is the exact output file (backward compatible).
                    writeParent(output);
                    YamlSupport.mapper().writeValue(output.toFile(), toWrite);
                    System.err.println("config-radar: wrote " + output + " (added=" + filtered.summary().added()
                        + ", removed=" + filtered.summary().removed()
                        + ", changed=" + filtered.summary().changed() + ")");
                } else {
                    // consumer mode: -o is the output directory. The diff YAML and all consumer
                    // artifacts (e.g. xac) are written into it, so they never collide with each other.
                    if (Files.exists(output) && !Files.isDirectory(output)) {
                        return fail("--consumer requires -o to be a directory, but it is an existing file: " + output);
                    }
                    Files.createDirectories(output);
                    var consumer = builtinDiffConsumers().find(consumerId);
                    if (consumer.isEmpty()) {
                        return fail("Unknown --consumer '" + consumerId + "'; available: " + builtinDiffConsumers().ids());
                    }
                    // Standalone diff report inside the directory, then the consumer's own files.
                    var diffPath = output.resolve("config-diff.yaml");
                    YamlSupport.mapper().writeValue(diffPath.toFile(), toWrite);
                    var context = new ConsumerContext(profile, region, namespace, parseContextProps(contextProps));
                    consumer.get().consume(toWrite, context, new DirectoryConsumerSink(output));
                    System.err.println("config-radar: wrote into " + output + " (added=" + filtered.summary().added()
                        + ", removed=" + filtered.summary().removed()
                        + ", changed=" + filtered.summary().changed() + ")");
                }
                return 0;
            } finally {
                // baseWt may be a worktree (needs git worktree remove) or an archive dir (plain rmdir).
                cleanupMaterialized(git, repoRoot, baseWt);
                cleanupMaterialized(git, repoRoot, headWt);
            }
        }

        /**
         * Materializes a commit into a directory: tries a git worktree first (full checkout), and
         * falls back to git archive (plain file extraction, lighter) when worktree fails. This gives
         * the best recovery on Windows/NTFS or locked worktrees without asking the user to intervene.
         */
        private void materializeCommit(GitClient git, Path repoRoot, String ref, Path target) throws Exception {
            try {
                git.addWorktree(repoRoot, ref, target);
            } catch (Exception worktreeError) {
                System.err.println("config-radar: worktree failed for " + ref + " (" + messageOf(worktreeError)
                    + "); falling back to git archive ...");
                git.archive(repoRoot, ref, target);
            }
        }

        /** Cleans up a materialized directory whether it is a worktree or an archive extract. */
        private static void cleanupMaterialized(GitClient git, Path repoRoot, Path path) {
            if (path == null) {
                return;
            }
            git.removeWorktree(repoRoot, path); // no-op if it was not a worktree
            // If it was an archive dir, removeWorktree's git call fails harmlessly; clean the dir.
            if (java.nio.file.Files.exists(path)) {
                try {
                    deleteRecursively(path);
                } catch (Exception ignored) {
                    // best-effort; OS will reclaim temp dirs eventually
                }
            }
        }

        private static void deleteRecursively(Path path) throws java.io.IOException {
            if (java.nio.file.Files.isDirectory(path)) {
                try (var entries = java.nio.file.Files.list(path)) {
                    for (var entry : entries.toList()) {
                        deleteRecursively(entry);
                    }
                }
            }
            java.nio.file.Files.deleteIfExists(path);
        }

        private static String messageOf(Throwable error) {
            var message = error.getMessage();
            return message != null ? message : error.getClass().getSimpleName();
        }

        /** Scans a worktree path into an inventory with the command's profile/region hints. */
        private ConfigInventory scan(Path projectRoot) throws Exception {
            var input = new ScanInput(
                projectRoot,
                java.util.List.of(),
                java.util.List.of(),
                null,
                new EnvironmentHints(profile, region, namespace),
                null
            );
            var options = new ScanOptions(
                false, true, 0, 0, null,
                redactSensitive ? RedactionPolicy.redactSensitive() : RedactionPolicy.disabled()
            );
            var result = ScanPipeline.defaults(false).scan(input, options, ConfigRules.empty());
            return result.inventory();
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

    /**
     * Builds the diff-consumer registry: core built-ins first, then any discovered via
     * ServiceLoader (e.g. the XAC module's diff consumer). Shared by the {@code diff} and
     * {@code config-diff} subcommands.
     */
    static DiffConsumerRegistry builtinDiffConsumers() {
        var registry = new DiffConsumerRegistry()
            .register(new YamlDiffConsumer());
        for (var discovered : java.util.ServiceLoader.load(DiffConsumer.class)) {
            registry.register(discovered);
        }
        return registry;
    }

    /** Parses {@code -D key=value} pairs into a map for the consumer context. Shared by subcommands. */
    static java.util.Map<String, String> parseContextProps(java.util.List<String> props) {
        var map = new java.util.LinkedHashMap<String, String>();
        for (var prop : props) {
            var eq = prop.indexOf('=');
            if (eq > 0) {
                map.put(prop.substring(0, eq), prop.substring(eq + 1));
            }
        }
        return map;
    }
}
