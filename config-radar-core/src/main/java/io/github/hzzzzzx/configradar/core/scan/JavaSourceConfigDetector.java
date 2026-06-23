package io.github.hzzzzzx.configradar.core.scan;

import com.fasterxml.jackson.databind.ObjectReader;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import io.github.hzzzzzx.configradar.core.io.YamlSupport;
import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.ConfigCenterDetails;
import io.github.hzzzzzx.configradar.core.model.ConfigValue;
import io.github.hzzzzzx.configradar.core.model.Confidence;
import io.github.hzzzzzx.configradar.core.model.DynamicKeyDetails;
import io.github.hzzzzzx.configradar.core.model.EnvironmentContext;
import io.github.hzzzzzx.configradar.core.model.ExternalDetails;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.model.JavaSystemPropertyDetails;
import io.github.hzzzzzx.configradar.core.model.ScanFinding;
import io.github.hzzzzzx.configradar.core.model.SourceKind;
import io.github.hzzzzzx.configradar.core.model.SourceLocation;
import io.github.hzzzzzx.configradar.core.model.SpringConfigurationPropertiesDetails;
import io.github.hzzzzzx.configradar.core.model.SpringPlaceholderDetails;
import io.github.hzzzzzx.configradar.core.model.UncertainFinding;
import io.github.hzzzzzx.configradar.core.model.UncertainReason;
import io.github.hzzzzzx.configradar.core.model.ValueType;
import io.github.hzzzzzx.configradar.core.rule.AnnotationRule;
import io.github.hzzzzzx.configradar.core.rule.MethodCallRule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/** Detects common Java/Spring configuration reads from source AST. */
public final class JavaSourceConfigDetector implements ConfigDetector {
    private static final ObjectReader YAML_READER = YamlSupport.mapper().readerFor(Object.class);
    private static final Pattern SPEL_ENVIRONMENT = Pattern.compile("environment\\[['\"]([^'\"]+)['\"]]");
    private static final Pattern SPEL_SYSTEM_ENVIRONMENT = Pattern.compile("systemEnvironment\\[['\"]([^'\"]+)['\"]]");
    private static final Pattern SPEL_SYSTEM_PROPERTIES = Pattern.compile("systemProperties\\[['\"]([^'\"]+)['\"]]");
    private static final Pattern SPEL_ENVIRONMENT_GET_PROPERTY =
        Pattern.compile("environment\\.getProperty\\(['\"]([^'\"]+)['\"]");
    private static final Pattern SPEL_SYSTEM_ENVIRONMENT_GET =
        Pattern.compile("systemEnvironment\\.get\\(['\"]([^'\"]+)['\"]");
    private static final Pattern SPEL_SYSTEM_PROPERTIES_GET_PROPERTY =
        Pattern.compile("systemProperties\\.getProperty\\(['\"]([^'\"]+)['\"]");
    private static final Pattern SPEL_SYSTEM_PROPERTIES_GET =
        Pattern.compile("systemProperties\\.get\\(['\"]([^'\"]+)['\"]");

    @Override
    public String id() {
        return "java-source-config";
    }

    private static final java.util.logging.Logger LOG =
        java.util.logging.Logger.getLogger(JavaSourceConfigDetector.class.getName());

    @Override
    public List<ScanFinding> detect(ScanContext context) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            LOG.warning("No system Java compiler available; Java source scanning is skipped.");
            return List.of();
        }

        var findings = new ArrayList<ScanFinding>();
        for (var file : context.fileIndex().ofType(FileType.JAVA)) {
            try {
                findings.addAll(scanFile(context, compiler, file));
            } catch (Exception error) {
                LOG.warning(() -> "Skipped unparseable Java source " + file.path() + ": " + errorMessage(error));
            }
        }
        return findings;
    }

    private static String errorMessage(Throwable error) {
        var message = error.getMessage();
        return message != null ? message : error.getClass().getSimpleName();
    }

    private List<ScanFinding> scanFile(ScanContext context, JavaCompiler compiler, IndexedFile file) throws IOException {
        var source = java.nio.file.Files.readString(file.path());
        var diagnostics = new DiagnosticCollector<javax.tools.JavaFileObject>();
        var unit = new SourceFile(file.path().toUri(), source);
        var task = (JavacTask) compiler.getTask(null, null, diagnostics, List.of("-proc:none"), null, List.of(unit));
        var parsed = task.parse();
        var trees = Trees.instance(task);
        var positions = trees.getSourcePositions();
        var findings = new ArrayList<ScanFinding>();
        for (var compilationUnit : parsed) {
            new Scanner(context, file, compilationUnit, positions, findings).scan(compilationUnit, null);
        }
        return findings;
    }

    private final class Scanner extends TreePathScanner<Void, Void> {
        private final ScanContext context;
        private final IndexedFile file;
        private final CompilationUnitTree unit;
        private final SourcePositions positions;
        private final List<ScanFinding> findings;
        private String className;
        /**
         * Local {@code static final String} constant names mapped to their literal values,
         * collected for the current compilation unit so that keys assembled from constants
         * (e.g. {@code PREFIX + ".host"}) can be resolved without cross-file type attribution.
         */
        private final java.util.Map<String, String> stringConstants = new java.util.HashMap<>();

        private Scanner(
            ScanContext context,
            IndexedFile file,
            CompilationUnitTree unit,
            SourcePositions positions,
            List<ScanFinding> findings
        ) {
            this.context = context;
            this.file = file;
            this.unit = unit;
            this.positions = positions;
            this.findings = findings;
        }

        @Override
        public Void visitClass(ClassTree tree, Void unused) {
            var previous = className;
            className = tree.getSimpleName().toString();
            for (var annotation : tree.getModifiers().getAnnotations()) {
                readConfigurationProperties(annotation, tree, className, true);
                readConditionalOnProperty(annotation, tree);
                readProfileAnnotation(annotation, tree);
                readPropertySourceAnnotation(annotation, tree);
                readPropertySourcesAnnotation(annotation, tree);
                readNacosPropertySourceAnnotation(annotation, tree);
                readAnnotationPlaceholders(annotation);
                readRuleAnnotation(annotation);
            }
            var result = super.visitClass(tree, unused);
            className = previous;
            return result;
        }

        @Override
        public Void visitVariable(VariableTree tree, Void unused) {
            registerStringConstant(tree);
            for (var annotation : tree.getModifiers().getAnnotations()) {
                readAnnotationPlaceholders(annotation);
                readConditionalOnProperty(annotation, tree);
                readRuleAnnotation(annotation);
            }
            return super.visitVariable(tree, unused);
        }

        /**
         * Records a {@code static final String} field initializer so subsequent key arguments
         * can be resolved without type attribution. Only string literals (and literal-only
         * concatenations) are recorded; anything more complex is left for the uncertain path.
         */
        private void registerStringConstant(VariableTree tree) {
            var modifiers = tree.getModifiers();
            if (modifiers == null) {
                return;
            }
            var flags = modifiers.getFlags();
            if (!flags.contains(javax.lang.model.element.Modifier.STATIC)
                || !flags.contains(javax.lang.model.element.Modifier.FINAL)) {
                return;
            }
            if (tree.getInitializer() == null) {
                return;
            }
            var resolved = literal(tree.getInitializer());
            if (resolved != null) {
                stringConstants.put(tree.getName().toString(), resolved);
            }
        }

        @Override
        public Void visitMethod(MethodTree tree, Void unused) {
            for (var annotation : tree.getModifiers().getAnnotations()) {
                readConfigurationProperties(annotation, tree, tree.getName().toString(), false);
                readConditionalOnProperty(annotation, tree);
                readProfileAnnotation(annotation, tree);
                readPropertySourcesAnnotation(annotation, tree);
                readAnnotationPlaceholders(annotation);
                readRuleAnnotation(annotation);
            }
            // Constructor and method parameter injections (e.g. @Value("#{...}") String x),
            // a very common Spring wiring style, are otherwise missed because the parameter
            // annotations live on VariableTree nodes inside the parameter list.
            for (var parameter : tree.getParameters()) {
                for (var annotation : parameter.getModifiers().getAnnotations()) {
                    readAnnotationPlaceholders(annotation);
                    readConditionalOnProperty(annotation, parameter);
                    readRuleAnnotation(annotation);
                }
            }
            return super.visitMethod(tree, unused);
        }

        @Override
        public Void visitAnnotation(AnnotationTree tree, Void unused) {
            readWebInitParamAnnotation(tree);
            return super.visitAnnotation(tree, unused);
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree tree, Void unused) {
            readSpringDefaultProperties(tree);
            readSpringCommandLineArgs(tree);
            readJvmInputArguments(tree);
            readSpringPlaceholderResolver(tree);
            readSpringAdditionalProfiles(tree);
            readSpringProfilePredicate(tree);
            readSystemPropertiesReplacement(tree);
            readApolloConfigRead(tree);
            readNacosConfigRead(tree);
            readJavaConfigRead(tree);
            readConsoleInput(tree);
            readServletInitParameter(tree);
            readJndiLookup(tree);
            readSpringBinder(tree);
            readJavaPreferences(tree);
            readResourceBundle(tree);
            readGenericConfigGetter(tree);
            readRuleMethodCall(tree);
            return super.visitMethodInvocation(tree, unused);
        }

        @Override
        public Void visitNewClass(NewClassTree tree, Void unused) {
            readSpringMapPropertySource(tree);
            readSpringPropertiesPropertySource(tree);
            readSpringResourcePropertySource(tree);
            return super.visitNewClass(tree, unused);
        }

        private void readAnnotationPlaceholders(AnnotationTree annotation) {
            if (annotationName(annotation).endsWith("ConditionalOnExpression")) {
                readConditionalOnExpression(annotation);
                return;
            }
            for (var argument : annotation.getArguments()) {
                var value = argument instanceof AssignmentTree assignment ? assignment.getExpression() : argument;
                readPlaceholders(value);
            }
        }

        private void readConditionalOnExpression(AnnotationTree annotation) {
            for (var expression : annotationValues(annotation, "value")) {
                addPlaceholders(expression, annotation, FindingRole.CONDITION);
                addSpelReferences(expression, annotation, FindingRole.CONDITION, "conditional-on-expression");
            }
        }

        private void readConfigurationProperties(
            AnnotationTree annotation,
            com.sun.source.tree.Tree tree,
            String boundType,
            boolean inferredFromFields
        ) {
            if (!annotationName(annotation).endsWith("ConfigurationProperties")) {
                return;
            }
            for (var argument : annotation.getArguments()) {
                var value = argument instanceof AssignmentTree assignment ? assignment.getExpression() : argument;
                var text = literal(value);
                if (text != null && !text.isBlank()) {
                    findings.add(new ConfigFinding(
                        text,
                        text,
                        FindingRole.READ,
                        null,
                        null,
                        EnvironmentContext.none(),
                        source(tree, SourceKind.JAVA),
                        Confidence.MEDIUM,
                        id(),
                        new SpringConfigurationPropertiesDetails(text, boundType, inferredFromFields)
                    ));
                }
            }
        }

        private void readWebInitParamAnnotation(AnnotationTree annotation) {
            if (!annotationName(annotation).endsWith("WebInitParam")) {
                return;
            }
            var key = annotationValue(annotation, "name");
            if (key == null || key.isBlank()) {
                return;
            }
            var value = annotationValue(annotation, "value");
            findings.add(new ConfigFinding(
                key,
                key,
                FindingRole.DEFINE,
                value == null ? null : new ConfigValue(value, value, typeOf(value)),
                null,
                EnvironmentContext.none(),
                source(annotation, SourceKind.JAVA),
                Confidence.HIGH,
                id(),
                new ExternalDetails("java", "web-init-param", null)
            ));
        }

        private void readConditionalOnProperty(AnnotationTree annotation, com.sun.source.tree.Tree sourceTree) {
            if (!annotationName(annotation).endsWith("ConditionalOnProperty")) {
                return;
            }
            var prefix = annotationValue(annotation, "prefix");
            var names = annotationValues(annotation, "name");
            if (names.isEmpty()) {
                names = annotationValues(annotation, "value");
            }
            if (names.isEmpty()) {
                return;
            }
            var havingValue = annotationValue(annotation, "havingValue");
            for (var name : names) {
                if (name == null || name.isBlank()) {
                    continue;
                }
                var key = prefix == null || prefix.isBlank() ? name : prefix + "." + name;
                findings.add(new ConfigFinding(
                    key,
                    key,
                    FindingRole.CONDITION,
                    null,
                    havingValue == null ? null : new ConfigValue(havingValue, havingValue, typeOf(havingValue)),
                    EnvironmentContext.none(),
                    source(sourceTree, SourceKind.JAVA),
                    Confidence.HIGH,
                    id(),
                    new ExternalDetails("spring", "conditional-on-property", null)
                ));
            }
        }

        private void readProfileAnnotation(AnnotationTree annotation, com.sun.source.tree.Tree sourceTree) {
            if (!annotationName(annotation).endsWith("Profile")) {
                return;
            }
            for (var profile : annotationValues(annotation, "value")) {
                if (profile == null || profile.isBlank()) {
                    continue;
                }
                findings.add(new ConfigFinding(
                    "spring.profiles",
                    "spring.profiles",
                    FindingRole.METADATA,
                    new ConfigValue(profile, profile, ValueType.STRING),
                    null,
                    new EnvironmentContext(profile, null, null),
                    source(sourceTree, SourceKind.JAVA),
                    Confidence.HIGH,
                    id(),
                    new ExternalDetails("spring", "profile", null)
                ));
            }
        }

        private void readPropertySourceAnnotation(AnnotationTree annotation, com.sun.source.tree.Tree sourceTree) {
            if (!annotationName(annotation).endsWith("PropertySource")) {
                return;
            }
            for (var location : annotationValues(annotation, "value")) {
                if (location == null || location.isBlank()) {
                    continue;
                }
                findings.add(new ConfigFinding(
                    "spring.property-source",
                    "spring.property-source",
                    FindingRole.METADATA,
                    new ConfigValue(location, location, ValueType.STRING),
                    null,
                    EnvironmentContext.none(),
                    source(sourceTree, SourceKind.JAVA),
                    Confidence.HIGH,
                    id(),
                    new ExternalDetails("spring", "property-source", null)
                ));
                readLocalPropertySource(location);
            }
        }

        private void readLocalPropertySource(String location) {
            var imported = localPropertySource(location);
            if (imported == null) {
                return;
            }
            // ponytail: properties only; add YAML/XML factory support when real projects need it.
            var properties = new Properties();
            try (var reader = Files.newBufferedReader(imported)) {
                properties.load(reader);
            } catch (IOException ignored) {
                return;
            }
            for (var name : properties.stringPropertyNames()) {
                var value = properties.getProperty(name);
                findings.add(new ConfigFinding(
                    name,
                    name,
                    FindingRole.DEFINE,
                    new ConfigValue(value, value, typeOf(value)),
                    null,
                    EnvironmentContext.none(),
                    importedSource(imported, name),
                    Confidence.HIGH,
                    id(),
                    new ExternalDetails("spring", "property-source-file", null)
                ));
                addPropertySourcePlaceholders(imported, name, value, location);
            }
        }

        private Path localPropertySource(String location) {
            var root = context.input().projectRoot();
            if (root == null) {
                return null;
            }
            var text = location.startsWith("optional:") ? location.substring("optional:".length()) : location;
            Path path;
            if (text.startsWith("classpath:")) {
                var resource = text.substring("classpath:".length());
                path = root.resolve("src/main/resources").resolve(resource.startsWith("/") ? resource.substring(1) : resource);
            } else if (text.startsWith("file:")) {
                path = Path.of(text.substring("file:".length()));
                if (!path.isAbsolute()) {
                    path = root.resolve(path);
                }
            } else {
                return null;
            }
            return Files.isRegularFile(path) && path.getFileName().toString().endsWith(".properties") ? path : null;
        }

        private void addPropertySourcePlaceholders(Path imported, String name, String value, String location) {
            var start = value.indexOf("${");
            while (start >= 0) {
                var end = placeholderEnd(value, start);
                if (end < 0) {
                    return;
                }
                var body = value.substring(start + 2, end);
                var split = placeholderSplit(body);
                var key = split < 0 ? body : body.substring(0, split);
                var defaultValue = split < 0 ? null : body.substring(split + (body.startsWith(":-", split) ? 2 : 1));
                if (!key.isBlank()) {
                    findings.add(new ConfigFinding(
                        key,
                        key,
                        FindingRole.READ,
                        null,
                        defaultValue == null ? null : new ConfigValue(defaultValue, defaultValue, typeOf(defaultValue)),
                        EnvironmentContext.none(),
                        importedSource(imported, name),
                        Confidence.HIGH,
                        id(),
                        new SpringPlaceholderDetails(defaultValue, location)
                    ));
                }
                start = value.indexOf("${", end + 1);
            }
        }

        private SourceLocation importedSource(Path imported, String key) {
            var root = context.input().projectRoot();
            var path = root == null ? imported : root.toAbsolutePath().relativize(imported.toAbsolutePath());
            return new SourceLocation(path.toString(), propertyLine(imported, key), className, SourceKind.PROPERTIES, file.scope());
        }

        private Integer propertyLine(Path imported, String key) {
            try {
                var lines = Files.readAllLines(imported);
                for (var index = 0; index < lines.size(); index++) {
                    var line = lines.get(index).trim();
                    if (line.startsWith(key + "=") || line.startsWith(key + ":")) {
                        return index + 1;
                    }
                }
            } catch (IOException ignored) {
                return null;
            }
            return null;
        }

        private void readPropertySourcesAnnotation(AnnotationTree annotation, com.sun.source.tree.Tree sourceTree) {
            if (!annotationName(annotation).endsWith("PropertySources")) {
                return;
            }
            for (var argument : annotation.getArguments()) {
                var value = argument instanceof AssignmentTree assignment ? assignment.getExpression() : argument;
                if (value instanceof NewArrayTree array) {
                    for (var nested : array.getInitializers()) {
                        if (nested instanceof AnnotationTree nestedAnnotation) {
                            readPropertySourceAnnotation(nestedAnnotation, sourceTree);
                        }
                    }
                } else if (value instanceof AnnotationTree nestedAnnotation) {
                    readPropertySourceAnnotation(nestedAnnotation, sourceTree);
                }
            }
        }

        private void readNacosPropertySourceAnnotation(AnnotationTree annotation, com.sun.source.tree.Tree sourceTree) {
            if (!annotationName(annotation).endsWith("NacosPropertySource")) {
                return;
            }
            var dataId = annotationValue(annotation, "dataId");
            if (dataId == null || dataId.isBlank()) {
                return;
            }
            var group = annotationValue(annotation, "groupId");
            var key = "nacos.config." + dataId;
            findings.add(new ConfigFinding(
                key,
                key,
                FindingRole.METADATA,
                null,
                null,
                EnvironmentContext.none(),
                source(sourceTree, SourceKind.JAVA),
                Confidence.HIGH,
                id(),
                new ConfigCenterDetails(null, group, dataId, null)
            ));
        }

        private void readSpringDefaultProperties(MethodInvocationTree tree) {
            var method = methodName(tree.getMethodSelect());
            var isSetDefaultProperties = method.endsWith(".setDefaultProperties")
                || method.equals("setDefaultProperties");
            var isSpringApplicationBuilderProperties = method.endsWith(".properties")
                && method.contains("SpringApplicationBuilder");
            if (!isSetDefaultProperties && !isSpringApplicationBuilderProperties) {
                return;
            }
            var properties = springDefaultProperties(tree.getArguments(), isSpringApplicationBuilderProperties);
            if (properties.isEmpty() && isSetDefaultProperties && !tree.getArguments().isEmpty()) {
                findings.add(new UncertainFinding(
                    tree.getArguments().getFirst().toString(),
                    UncertainReason.MAP_DRIVEN_KEY,
                    method,
                    null,
                    source(tree, SourceKind.JAVA),
                    Confidence.LOW,
                    id(),
                    new DynamicKeyDetails(null, null, tree.getArguments().getFirst().toString())
                ));
            }
            for (var property : properties) {
                findings.add(new ConfigFinding(
                    property.key(),
                    property.key(),
                    FindingRole.DEFINE,
                    property.value() == null ? null : new ConfigValue(property.value(), property.value(), typeOf(property.value())),
                    null,
                    EnvironmentContext.none(),
                    source(tree, SourceKind.JAVA),
                    Confidence.HIGH,
                    id(),
                    new ExternalDetails("spring", "default-properties", null)
                ));
            }
        }

        private void readSpringCommandLineArgs(MethodInvocationTree tree) {
            var method = methodName(tree.getMethodSelect());
            if (!method.endsWith(".run") || !method.contains("SpringApplication")) {
                return;
            }
            var arguments = tree.getArguments();
            var firstConfigArgument = method.contains("SpringApplicationBuilder") ? 0 : 1;
            for (var index = firstConfigArgument; index < arguments.size(); index++) {
                var argument = arguments.get(index);
                var values = stringLiterals(argument);
                if (values.isEmpty()) {
                    findings.add(new UncertainFinding(
                        argument.toString(),
                        UncertainReason.COMMAND_LINE_ARGS,
                        method,
                        null,
                        source(tree, SourceKind.JAVA),
                        Confidence.LOW,
                        id(),
                        new DynamicKeyDetails(null, null, argument.toString())
                    ));
                    continue;
                }
                for (var value : values) {
                    var property = commandLineProperty(value);
                    if (property == null) {
                        findings.add(new UncertainFinding(
                            value,
                            UncertainReason.COMMAND_LINE_ARGS,
                            method,
                            null,
                            source(tree, SourceKind.JAVA),
                            Confidence.LOW,
                            id(),
                            new DynamicKeyDetails(null, null, value)
                        ));
                        continue;
                    }
                    findings.add(new ConfigFinding(
                        property.key(),
                        property.key(),
                        FindingRole.DEFINE,
                        property.value() == null ? null : new ConfigValue(property.value(), property.value(), typeOf(property.value())),
                        null,
                        EnvironmentContext.none(),
                        source(tree, SourceKind.JAVA),
                        Confidence.HIGH,
                        id(),
                        new ExternalDetails("spring", "command-line-args", null)
                    ));
                }
            }
        }

        private void readJvmInputArguments(MethodInvocationTree tree) {
            var method = methodName(tree.getMethodSelect());
            if (!method.endsWith(".getInputArguments") || !method.contains("ManagementFactory.getRuntimeMXBean()")) {
                return;
            }
            findings.add(new UncertainFinding(
                tree.toString(),
                UncertainReason.COMMAND_LINE_ARGS,
                method,
                null,
                source(tree, SourceKind.JAVA),
                Confidence.LOW,
                id(),
                new DynamicKeyDetails("-D", null, tree.toString())
            ));
        }

        private void readSpringPlaceholderResolver(MethodInvocationTree tree) {
            var method = methodName(tree.getMethodSelect());
            if (!method.endsWith(".resolvePlaceholders") && !method.endsWith(".resolveRequiredPlaceholders")) {
                return;
            }
            if (!tree.getArguments().isEmpty()) {
                readPlaceholders(tree.getArguments().getFirst());
            }
        }

        private void readSpringAdditionalProfiles(MethodInvocationTree tree) {
            var method = methodName(tree.getMethodSelect());
            var isBuilderProfiles = method.endsWith(".profiles") && method.contains("SpringApplicationBuilder");
            if (!method.endsWith(".setAdditionalProfiles") && !isBuilderProfiles) {
                return;
            }
            for (var argument : tree.getArguments()) {
                for (var profile : stringLiterals(argument)) {
                    if (profile.isBlank()) {
                        continue;
                    }
                    findings.add(new ConfigFinding(
                        "spring.profiles",
                        "spring.profiles",
                        FindingRole.METADATA,
                        new ConfigValue(profile, profile, ValueType.STRING),
                        null,
                        new EnvironmentContext(profile, null, null),
                        source(tree, SourceKind.JAVA),
                        Confidence.HIGH,
                        id(),
                        new ExternalDetails("spring", "additional-profile", null)
                    ));
                }
            }
        }

        private void readSpringProfilePredicate(MethodInvocationTree tree) {
            var method = methodName(tree.getMethodSelect());
            if (!method.endsWith(".acceptsProfiles") && !method.endsWith(".matchesProfiles")) {
                return;
            }
            for (var argument : tree.getArguments()) {
                for (var profile : stringLiterals(argument)) {
                    if (profile.isBlank()) {
                        continue;
                    }
                    findings.add(new ConfigFinding(
                        "spring.profiles",
                        "spring.profiles",
                        FindingRole.CONDITION,
                        new ConfigValue(profile, profile, ValueType.STRING),
                        null,
                        new EnvironmentContext(profile, null, null),
                        source(tree, SourceKind.JAVA),
                        Confidence.HIGH,
                        id(),
                        new ExternalDetails("spring", "profile-condition", null)
                    ));
                }
            }
        }

        private void readSpringMapPropertySource(NewClassTree tree) {
            if (!tree.getIdentifier().toString().endsWith("MapPropertySource") || tree.getArguments().size() < 2) {
                return;
            }
            for (var property : springDefaultProperties(List.of(tree.getArguments().get(1)), false)) {
                findings.add(new ConfigFinding(
                    property.key(),
                    property.key(),
                    FindingRole.DEFINE,
                    property.value() == null ? null : new ConfigValue(property.value(), property.value(), typeOf(property.value())),
                    null,
                    EnvironmentContext.none(),
                    source(tree, SourceKind.JAVA),
                    Confidence.HIGH,
                    id(),
                    new ExternalDetails("spring", "map-property-source", null)
                ));
            }
        }

        private void readSpringPropertiesPropertySource(NewClassTree tree) {
            if (!tree.getIdentifier().toString().endsWith("PropertiesPropertySource") || tree.getArguments().size() < 2) {
                return;
            }
            findings.add(new UncertainFinding(
                tree.getArguments().get(1).toString(),
                UncertainReason.MAP_DRIVEN_KEY,
                tree.getIdentifier().toString(),
                null,
                source(tree, SourceKind.JAVA),
                Confidence.LOW,
                id(),
                new DynamicKeyDetails(null, null, tree.getArguments().get(1).toString())
            ));
        }

        private void readSpringResourcePropertySource(NewClassTree tree) {
            if (!tree.getIdentifier().toString().endsWith("ResourcePropertySource") || tree.getArguments().isEmpty()) {
                return;
            }
            var locationArg = tree.getArguments().size() > 1 ? tree.getArguments().get(1) : tree.getArguments().getFirst();
            var location = literal(locationArg);
            if (location == null || location.isBlank()) {
                return;
            }
            findings.add(new ConfigFinding(
                "spring.property-source",
                "spring.property-source",
                FindingRole.METADATA,
                new ConfigValue(location, location, ValueType.STRING),
                null,
                EnvironmentContext.none(),
                source(tree, SourceKind.JAVA),
                Confidence.HIGH,
                id(),
                new ExternalDetails("spring", "resource-property-source", null)
            ));
            readLocalPropertySource(location);
        }

        private void readJavaConfigRead(MethodInvocationTree tree) {
            var method = methodName(tree.getMethodSelect());
            if (isApolloGetProperty(tree)) {
                return;
            }
            var isGetProperty = method.endsWith(".getProperty") || method.equals("getProperty");
            var isGetRequiredProperty = method.endsWith(".getRequiredProperty") || method.equals("getRequiredProperty");
            var isContainsProperty = method.endsWith(".containsProperty") || method.equals("containsProperty");
            var isSetProperty = method.endsWith(".setProperty") || method.equals("setProperty");
            var isClearProperty = method.endsWith(".clearProperty") || method.equals("clearProperty");
            var isSystemPropertiesGetProperty = method.endsWith("System.getProperties().getProperty");
            var isSystemPropertiesGet = method.endsWith("System.getProperties().get");
            var isSystemPropertiesGetOrDefault = method.endsWith("System.getProperties().getOrDefault");
            var isSystemPropertiesContainsKey = method.endsWith("System.getProperties().containsKey");
            var isSystemPropertiesPut = method.endsWith("System.getProperties().put");
            var isSystemPropertiesPutIfAbsent = method.endsWith("System.getProperties().putIfAbsent");
            var isSystemPropertiesReplace = method.endsWith("System.getProperties().replace");
            var isSystemPropertiesRemove = method.endsWith("System.getProperties().remove");
            var isGetenv = method.endsWith(".getenv") || method.equals("getenv");
            var isGetenvMapGet = method.endsWith("System.getenv().get");
            var isGetenvMapGetOrDefault = method.endsWith("System.getenv().getOrDefault");
            var isGetenvMapContainsKey = method.endsWith("System.getenv().containsKey");
            var isIntegerGetInteger = method.equals("Integer.getInteger") || method.endsWith(".Integer.getInteger");
            var isLongGetLong = method.equals("Long.getLong") || method.endsWith(".Long.getLong");
            var isBooleanGetBoolean = method.equals("Boolean.getBoolean") || method.endsWith(".Boolean.getBoolean");
            var isTypedSystemProperty = isIntegerGetInteger || isLongGetLong || isBooleanGetBoolean;
            if (!isGetProperty && !isGetRequiredProperty && !isContainsProperty && !isSetProperty && !isClearProperty
                && !isSystemPropertiesGetProperty && !isSystemPropertiesGet && !isSystemPropertiesGetOrDefault
                && !isSystemPropertiesContainsKey && !isSystemPropertiesPut && !isSystemPropertiesPutIfAbsent
                && !isSystemPropertiesReplace && !isSystemPropertiesRemove && !isGetenv && !isGetenvMapGet
                && !isGetenvMapGetOrDefault && !isGetenvMapContainsKey && !isTypedSystemProperty) {
                return;
            }
            var args = tree.getArguments();
            if (args.isEmpty()) {
                return;
            }
            var key = literal(args.getFirst());
            var value = (isSetProperty || isSystemPropertiesPut || isSystemPropertiesPutIfAbsent
                || isSystemPropertiesReplace) && args.size() > 1
                ? literalValue(args.get(args.size() - 1))
                : null;
            var defaultValue = defaultValue(
                args,
                isGetProperty || isSystemPropertiesGetProperty,
                isGetenv || isBooleanGetBoolean || isSetProperty || isClearProperty || isGetenvMapGet || isGetenvMapContainsKey
                    || isSystemPropertiesGet || isSystemPropertiesContainsKey || isSystemPropertiesPut
                    || isSystemPropertiesPutIfAbsent
                    || isSystemPropertiesReplace
                    || isSystemPropertiesRemove
            );
            if (key == null) {
                findings.add(new UncertainFinding(
                    args.getFirst().toString(),
                    args.getFirst() instanceof BinaryTree ? UncertainReason.STRING_CONCAT : UncertainReason.UNKNOWN,
                    method,
                    null,
                    source(tree, SourceKind.JAVA),
                    Confidence.LOW,
                    id(),
                    new DynamicKeyDetails(null, null, args.getFirst().toString())
                ));
                return;
            }
            findings.add(new ConfigFinding(
                key,
                key,
                isSetProperty || isClearProperty || isSystemPropertiesPut || isSystemPropertiesPutIfAbsent
                    || isSystemPropertiesReplace || isSystemPropertiesRemove
                    ? FindingRole.DEFINE
                    : FindingRole.READ,
                value == null ? null : new ConfigValue(value, value, typeOf(value)),
                defaultValue == null ? null : new ConfigValue(defaultValue, defaultValue, typeOf(defaultValue)),
                EnvironmentContext.none(),
                source(tree, SourceKind.JAVA),
                Confidence.HIGH,
                id(),
                new JavaSystemPropertyDetails(defaultValue, false)
            ));
            expandSpringApplicationJson(key, value, tree);
        }

        private void readApolloConfigRead(MethodInvocationTree tree) {
            if (!isApolloGetProperty(tree)) {
                return;
            }
            var args = tree.getArguments();
            if (args.isEmpty()) {
                return;
            }
            var key = literal(args.getFirst());
            if (key == null) {
                findings.add(new UncertainFinding(
                    args.getFirst().toString(),
                    args.getFirst() instanceof BinaryTree ? UncertainReason.STRING_CONCAT : UncertainReason.UNKNOWN,
                    "apollo.getProperty",
                    null,
                    source(tree, SourceKind.JAVA),
                    Confidence.LOW,
                    id(),
                    new DynamicKeyDetails(apolloNamespace(tree), null, args.getFirst().toString())
                ));
                return;
            }
            var defaultValue = args.size() > 1 ? literalValue(args.get(1)) : null;
            findings.add(new ConfigFinding(
                key,
                key,
                FindingRole.READ,
                null,
                defaultValue == null ? null : new ConfigValue(defaultValue, defaultValue, typeOf(defaultValue)),
                EnvironmentContext.none(),
                source(tree, SourceKind.JAVA),
                Confidence.HIGH,
                id(),
                new ConfigCenterDetails(apolloNamespace(tree), null, null, defaultValue)
            ));
        }

        private boolean isApolloGetProperty(MethodInvocationTree tree) {
            if (!(tree.getMethodSelect() instanceof MemberSelectTree select)
                || !select.getIdentifier().contentEquals("getProperty")) {
                return false;
            }
            var expression = select.getExpression();
            return expression.toString().startsWith("ConfigService.getAppConfig()")
                || expression.toString().startsWith("ConfigService.getConfig(")
                || expression.toString().startsWith("com.ctrip.framework.apollo.ConfigService.getAppConfig()")
                || expression.toString().startsWith("com.ctrip.framework.apollo.ConfigService.getConfig(");
        }

        private String apolloNamespace(MethodInvocationTree tree) {
            if (!(tree.getMethodSelect() instanceof MemberSelectTree select)
                || !(select.getExpression() instanceof MethodInvocationTree configCall)) {
                return "application";
            }
            var method = methodName(configCall.getMethodSelect());
            if (method.endsWith(".getConfig") && !configCall.getArguments().isEmpty()) {
                var namespace = literal(configCall.getArguments().getFirst());
                return namespace == null || namespace.isBlank() ? null : namespace;
            }
            return "application";
        }

        private void readNacosConfigRead(MethodInvocationTree tree) {
            if (!isNacosGetConfig(tree)) {
                return;
            }
            var args = tree.getArguments();
            var dataId = literal(args.get(0));
            if (dataId == null || dataId.isBlank()) {
                findings.add(new UncertainFinding(
                    args.get(0).toString(),
                    args.get(0) instanceof BinaryTree ? UncertainReason.STRING_CONCAT : UncertainReason.UNKNOWN,
                    "nacos.getConfig",
                    null,
                    source(tree, SourceKind.JAVA),
                    Confidence.LOW,
                    id(),
                    new DynamicKeyDetails(null, literal(args.get(1)), args.get(0).toString())
                ));
                return;
            }
            var group = literal(args.get(1));
            var key = "nacos.config." + dataId;
            findings.add(new ConfigFinding(
                key,
                key,
                FindingRole.METADATA,
                null,
                null,
                EnvironmentContext.none(),
                source(tree, SourceKind.JAVA),
                Confidence.HIGH,
                id(),
                new ConfigCenterDetails(null, group, dataId, null)
            ));
        }

        private boolean isNacosGetConfig(MethodInvocationTree tree) {
            var method = methodName(tree.getMethodSelect());
            return (method.equals("ConfigService.getConfig") || method.endsWith(".ConfigService.getConfig"))
                && tree.getArguments().size() >= 2;
        }

        private void readSystemPropertiesReplacement(MethodInvocationTree tree) {
            var method = methodName(tree.getMethodSelect());
            if (!method.endsWith("System.setProperties") || tree.getArguments().isEmpty()) {
                return;
            }
            findings.add(new UncertainFinding(
                tree.getArguments().getFirst().toString(),
                UncertainReason.MAP_DRIVEN_KEY,
                method,
                null,
                source(tree, SourceKind.JAVA),
                Confidence.LOW,
                id(),
                new DynamicKeyDetails(null, null, tree.getArguments().getFirst().toString())
            ));
        }

        private void readConsoleInput(MethodInvocationTree tree) {
            var method = methodName(tree.getMethodSelect());
            var isConsoleInput = method.endsWith("System.console().readLine")
                || method.endsWith("System.console().readPassword");
            if (!isConsoleInput) {
                return;
            }
            var args = tree.getArguments();
            var prompt = args.isEmpty() ? null : literalValue(args.getFirst());
            var expression = prompt == null || prompt.isBlank() ? tree.toString() : prompt;
            findings.add(new UncertainFinding(
                expression,
                UncertainReason.USER_INPUT,
                method,
                null,
                source(tree, SourceKind.JAVA),
                Confidence.LOW,
                id(),
                new DynamicKeyDetails(null, null, tree.toString())
            ));
        }

        private void readServletInitParameter(MethodInvocationTree tree) {
            var method = methodName(tree.getMethodSelect());
            if (!method.endsWith(".getInitParameter") && !method.equals("getInitParameter")) {
                return;
            }
            var args = tree.getArguments();
            if (args.isEmpty()) {
                return;
            }
            var key = literal(args.getFirst());
            if (key == null || key.isBlank()) {
                findings.add(new UncertainFinding(
                    args.getFirst().toString(),
                    args.getFirst() instanceof BinaryTree ? UncertainReason.STRING_CONCAT : UncertainReason.UNKNOWN,
                    method,
                    null,
                    source(tree, SourceKind.JAVA),
                    Confidence.LOW,
                    id(),
                    new DynamicKeyDetails(null, null, args.getFirst().toString())
                ));
                return;
            }
            findings.add(new ConfigFinding(
                key,
                key,
                FindingRole.READ,
                null,
                null,
                EnvironmentContext.none(),
                source(tree, SourceKind.JAVA),
                Confidence.MEDIUM,
                id(),
                new ExternalDetails("java", "servlet-init-parameter", null)
            ));
        }

        private void readJndiLookup(MethodInvocationTree tree) {
            var method = methodName(tree.getMethodSelect());
            if (!method.endsWith(".lookup") && !method.equals("lookup")) {
                return;
            }
            var args = tree.getArguments();
            if (args.isEmpty()) {
                return;
            }
            var key = literal(args.getFirst());
            if (key == null || key.isBlank()) {
                findings.add(new UncertainFinding(
                    args.getFirst().toString(),
                    args.getFirst() instanceof BinaryTree ? UncertainReason.STRING_CONCAT : UncertainReason.UNKNOWN,
                    method,
                    null,
                    source(tree, SourceKind.JAVA),
                    Confidence.LOW,
                    id(),
                    new DynamicKeyDetails(null, null, args.getFirst().toString())
                ));
                return;
            }
            if (!key.startsWith("java:comp/env/")) {
                return;
            }
            findings.add(new ConfigFinding(
                key,
                key,
                FindingRole.READ,
                null,
                null,
                EnvironmentContext.none(),
                source(tree, SourceKind.JAVA),
                Confidence.MEDIUM,
                id(),
                new ExternalDetails("java", "jndi-lookup", null)
            ));
        }

        private void readSpringBinder(MethodInvocationTree tree) {
            var method = methodName(tree.getMethodSelect());
            var isBinderBind = (method.endsWith(".bind") || method.endsWith(".bindOrCreate"))
                && method.contains("Binder.get(");
            if (!isBinderBind || tree.getArguments().isEmpty()) {
                return;
            }
            var key = literal(tree.getArguments().getFirst());
            if (key == null || key.isBlank()) {
                return;
            }
            findings.add(new ConfigFinding(
                key,
                key,
                FindingRole.READ,
                null,
                null,
                EnvironmentContext.none(),
                source(tree, SourceKind.JAVA),
                Confidence.HIGH,
                id(),
                new ExternalDetails("spring", "binder", null)
            ));
        }

        private void readGenericConfigGetter(MethodInvocationTree tree) {
            if (!(tree.getMethodSelect() instanceof MemberSelectTree select) || tree.getArguments().isEmpty()) {
                return;
            }
            var getter = select.getIdentifier().toString();
            if (!List.of(
                "getString",
                "getInt",
                "getLong",
                "getBoolean",
                "getDouble",
                "getDuration",
                "getStringList",
                "getIntList",
                "getLongList",
                "getBooleanList",
                "getDoubleList",
                "getConfig",
                "hasPath",
                "getValue",
                "getOptionalValue",
                "getConfigValue"
            ).contains(getter)) {
                return;
            }
            var receiver = select.getExpression().toString().toLowerCase(java.util.Locale.ROOT);
            if (!receiver.contains("config") && !receiver.contains("configuration") && !receiver.equals("cfg")) {
                return;
            }
            if (getter.equals("getConfig") && receiver.contains("configservice")) {
                return;
            }
            var args = tree.getArguments();
            var key = literal(args.getFirst());
            if (key == null || key.isBlank()) {
                findings.add(new UncertainFinding(
                    args.getFirst().toString(),
                    args.getFirst() instanceof BinaryTree ? UncertainReason.STRING_CONCAT : UncertainReason.UNKNOWN,
                    methodName(tree.getMethodSelect()),
                    null,
                    source(tree, SourceKind.JAVA),
                    Confidence.LOW,
                    id(),
                    new DynamicKeyDetails(null, null, args.getFirst().toString())
                ));
                return;
            }
            var defaultValue = args.size() > 1 && !List.of("getValue", "getOptionalValue", "getConfigValue").contains(getter)
                ? literalValue(args.get(1))
                : null;
            findings.add(new ConfigFinding(
                key,
                key,
                FindingRole.READ,
                null,
                defaultValue == null ? null : new ConfigValue(defaultValue, defaultValue, typeOf(defaultValue)),
                EnvironmentContext.none(),
                source(tree, SourceKind.JAVA),
                Confidence.MEDIUM,
                id(),
                new ExternalDetails("java", "generic-config-getter", null)
            ));
        }

        private void readJavaPreferences(MethodInvocationTree tree) {
            if (!(tree.getMethodSelect() instanceof MemberSelectTree select) || tree.getArguments().isEmpty()) {
                return;
            }
            var getter = select.getIdentifier().toString();
            if (!List.of(
                "get",
                "getInt",
                "getLong",
                "getBoolean",
                "getFloat",
                "getDouble",
                "getByteArray"
            ).contains(getter)) {
                return;
            }
            var receiver = select.getExpression().toString().toLowerCase(java.util.Locale.ROOT);
            if (!receiver.contains("preferences") && !receiver.equals("prefs") && !receiver.endsWith(".prefs")) {
                return;
            }
            var args = tree.getArguments();
            var key = literal(args.getFirst());
            if (key == null || key.isBlank()) {
                findings.add(new UncertainFinding(
                    args.getFirst().toString(),
                    args.getFirst() instanceof BinaryTree ? UncertainReason.STRING_CONCAT : UncertainReason.UNKNOWN,
                    methodName(tree.getMethodSelect()),
                    null,
                    source(tree, SourceKind.JAVA),
                    Confidence.LOW,
                    id(),
                    new DynamicKeyDetails(null, null, args.getFirst().toString())
                ));
                return;
            }
            var defaultValue = args.size() > 1 ? literalValue(args.get(1)) : null;
            findings.add(new ConfigFinding(
                key,
                key,
                FindingRole.READ,
                null,
                defaultValue == null ? null : new ConfigValue(defaultValue, defaultValue, typeOf(defaultValue)),
                EnvironmentContext.none(),
                source(tree, SourceKind.JAVA),
                Confidence.MEDIUM,
                id(),
                new ExternalDetails("java", "preferences", null)
            ));
        }

        private void readResourceBundle(MethodInvocationTree tree) {
            if (!(tree.getMethodSelect() instanceof MemberSelectTree select) || tree.getArguments().isEmpty()) {
                return;
            }
            var getter = select.getIdentifier().toString();
            if (!List.of("getString", "getObject", "containsKey").contains(getter)) {
                return;
            }
            var receiver = select.getExpression().toString().toLowerCase(java.util.Locale.ROOT);
            if (!receiver.contains("resourcebundle") && !receiver.equals("bundle") && !receiver.endsWith(".bundle")) {
                return;
            }
            var args = tree.getArguments();
            var key = literal(args.getFirst());
            if (key == null || key.isBlank()) {
                findings.add(new UncertainFinding(
                    args.getFirst().toString(),
                    args.getFirst() instanceof BinaryTree ? UncertainReason.STRING_CONCAT : UncertainReason.UNKNOWN,
                    methodName(tree.getMethodSelect()),
                    null,
                    source(tree, SourceKind.JAVA),
                    Confidence.LOW,
                    id(),
                    new DynamicKeyDetails(null, null, args.getFirst().toString())
                ));
                return;
            }
            findings.add(new ConfigFinding(
                key,
                key,
                FindingRole.READ,
                null,
                null,
                EnvironmentContext.none(),
                source(tree, SourceKind.JAVA),
                Confidence.MEDIUM,
                id(),
                new ExternalDetails("java", "resource-bundle", null)
            ));
        }

        private void readRuleMethodCall(MethodInvocationTree tree) {
            var method = methodName(tree.getMethodSelect());
            for (var rule : context.rules().methodCalls()) {
                if (!matches(rule, method)) {
                    continue;
                }
                var args = tree.getArguments();
                if (rule.keyArg() < 0 || rule.keyArg() >= args.size()) {
                    continue;
                }
                var key = literal(args.get(rule.keyArg()));
                var defaultValue = rule.defaultArg() == null || rule.defaultArg() >= args.size()
                    ? null
                    : literal(args.get(rule.defaultArg()));
                var value = rule.valueArg() == null || rule.valueArg() < 0 || rule.valueArg() >= args.size()
                    ? null
                    : literalValue(args.get(rule.valueArg()));
                if (key == null) {
                    findings.add(new UncertainFinding(
                        args.get(rule.keyArg()).toString(),
                        UncertainReason.UNKNOWN,
                        method,
                        null,
                        source(tree, SourceKind.JAVA),
                        Confidence.LOW,
                        id(),
                        new DynamicKeyDetails(null, null, args.get(rule.keyArg()).toString())
                    ));
                    continue;
                }
                findings.add(externalFinding(rule.id(), key, value, defaultValue, rule.confidence(), rule.role(), tree));
            }
        }

        private void readRuleAnnotation(AnnotationTree annotation) {
            for (var rule : context.rules().annotations()) {
                if (!annotationName(annotation).endsWith(simple(rule.type()))) {
                    continue;
                }
                var key = annotationValue(annotation, rule.keyAttribute());
                if (key == null || key.isBlank()) {
                    continue;
                }
                var value = annotationValue(annotation, rule.valueAttribute());
                var defaultValue = annotationValue(annotation, rule.defaultAttribute());
                findings.add(externalFinding(rule.id(), key, value, defaultValue, rule.confidence(), rule.role(), annotation));
            }
        }

        private void addPlaceholder(String raw, ExpressionTree tree) {
            addPlaceholders(raw, tree, FindingRole.READ);
        }

        private void addPlaceholders(
            String raw,
            com.sun.source.tree.Tree tree,
            FindingRole role
        ) {
            var start = raw.indexOf("${");
            while (start >= 0) {
                var end = placeholderEnd(raw, start);
                if (end < 0) {
                    return;
                }
                addPlaceholderBody(raw.substring(start + 2, end), raw, tree, role);
                start = raw.indexOf("${", end + 1);
            }
        }

        private void addPlaceholderBody(
            String body,
            String raw,
            com.sun.source.tree.Tree tree,
            FindingRole role
        ) {
            var split = placeholderSplit(body);
            var key = split < 0 ? body : body.substring(0, split);
            var defaultValue = split < 0 ? null : body.substring(split + (body.startsWith(":-", split) ? 2 : 1));
            if (key.isBlank()) {
                return;
            }
            findings.add(new ConfigFinding(
                key,
                key,
                role,
                null,
                defaultValue == null ? null : new ConfigValue(defaultValue, defaultValue, typeOf(defaultValue)),
                EnvironmentContext.none(),
                source(tree, SourceKind.JAVA),
                Confidence.HIGH,
                id(),
                new SpringPlaceholderDetails(defaultValue, raw)
            ));
            if (defaultValue != null) {
                addPlaceholders(defaultValue, tree, role);
            }
        }

        private int placeholderEnd(String raw, int start) {
            var depth = 0;
            for (var index = start; index < raw.length(); index++) {
                if (raw.startsWith("${", index)) {
                    depth++;
                    index++;
                    continue;
                }
                if (raw.charAt(index) == '}') {
                    depth--;
                    if (depth == 0) {
                        return index;
                    }
                }
            }
            return -1;
        }

        private int placeholderSplit(String body) {
            var depth = 0;
            for (var index = 0; index < body.length(); index++) {
                if (body.startsWith("${", index)) {
                    depth++;
                    index++;
                    continue;
                }
                var character = body.charAt(index);
                if (character == '}') {
                    depth--;
                    continue;
                }
                if (character == ':' && depth == 0) {
                    return index;
                }
            }
            return -1;
        }

        private SourceLocation source(com.sun.source.tree.Tree tree, SourceKind sourceKind) {
            var start = positions.getStartPosition(unit, tree);
            var line = start < 0 ? null : (int) unit.getLineMap().getLineNumber(start);
            var root = context.input().projectRoot();
            var path = root == null ? file.path() : root.toAbsolutePath().relativize(file.path().toAbsolutePath());
            return new SourceLocation(path.toString(), line, className, sourceKind, file.scope());
        }

        private String annotationName(AnnotationTree annotation) {
            return annotation.getAnnotationType().toString();
        }

        private String methodName(ExpressionTree tree) {
            if (tree instanceof MemberSelectTree select) {
                return methodName(select.getExpression()) + "." + select.getIdentifier();
            }
            return tree.toString();
        }

        private String literal(ExpressionTree tree) {
            if (tree instanceof LiteralTree literal && literal.getValue() instanceof String text) {
                return text;
            }
            if (tree instanceof NewArrayTree array && !array.getInitializers().isEmpty()) {
                return literal(array.getInitializers().getFirst());
            }
            // Local static final String constant reference, e.g. getProperty(PREFIX + ".host").
            if (tree instanceof com.sun.source.tree.IdentifierTree identifier) {
                return stringConstants.get(identifier.getName().toString());
            }
            // String concatenation of literal/constant operands, e.g. "db." + "host" or PREFIX + ".host".
            // Only fully resolvable operands collapse to a literal; mixed dynamic operands stay uncertain.
            if (tree instanceof BinaryTree binary) {
                var left = literal(binary.getLeftOperand());
                var right = literal(binary.getRightOperand());
                if (left != null && right != null) {
                    return left + right;
                }
            }
            return null;
        }

        private String literalValue(ExpressionTree tree) {
            if (tree instanceof LiteralTree literal && literal.getValue() != null) {
                return literal.getValue().toString();
            }
            return literal(tree);
        }

        private List<PropertyPair> springDefaultProperties(
            List<? extends ExpressionTree> args,
            boolean stringProperties
        ) {
            if (stringProperties) {
                var pairs = args.stream()
                    .flatMap(argument -> stringLiterals(argument).stream())
                    .map(this::propertyString)
                    .filter(java.util.Objects::nonNull)
                    .toList();
                if (!pairs.isEmpty()) {
                    return pairs;
                }
            }
            if (args.size() != 1 || !(args.getFirst() instanceof MethodInvocationTree mapFactory)) {
                return List.of();
            }
            var factory = methodName(mapFactory.getMethodSelect());
            if (factory.endsWith(".Map.ofEntries") || factory.equals("Map.ofEntries")) {
                return mapEntries(mapFactory.getArguments());
            }
            if (!factory.endsWith(".Map.of") && !factory.equals("Map.of")) {
                return List.of();
            }
            var values = mapFactory.getArguments();
            var pairs = new ArrayList<PropertyPair>();
            for (var index = 0; index + 1 < values.size(); index += 2) {
                var key = literal(values.get(index));
                var value = literalValue(values.get(index + 1));
                if (key != null && !key.isBlank()) {
                    pairs.add(new PropertyPair(key, value));
                }
            }
            return pairs;
        }

        private List<String> stringLiterals(ExpressionTree tree) {
            if (tree instanceof NewArrayTree array) {
                return array.getInitializers().stream()
                    .map(this::literal)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            }
            var literal = literal(tree);
            return literal == null ? List.of() : List.of(literal);
        }

        private List<PropertyPair> mapEntries(List<? extends ExpressionTree> entries) {
            var pairs = new ArrayList<PropertyPair>();
            for (var entry : entries) {
                if (!(entry instanceof MethodInvocationTree invocation)) {
                    continue;
                }
                var method = methodName(invocation.getMethodSelect());
                if (!method.endsWith(".Map.entry") && !method.equals("Map.entry")) {
                    continue;
                }
                var args = invocation.getArguments();
                if (args.size() < 2) {
                    continue;
                }
                var key = literal(args.get(0));
                var value = literalValue(args.get(1));
                if (key != null && !key.isBlank()) {
                    pairs.add(new PropertyPair(key, value));
                }
            }
            return pairs;
        }

        private PropertyPair propertyString(String text) {
            if (text == null) {
                return null;
            }
            var equals = text.indexOf('=');
            var colon = text.indexOf(':');
            var split = equals >= 0 && (colon < 0 || equals < colon) ? equals : colon;
            if (split <= 0) {
                return null;
            }
            var key = text.substring(0, split).trim();
            if (key.isEmpty()) {
                return null;
            }
            return new PropertyPair(key, text.substring(split + 1).trim());
        }

        private PropertyPair commandLineProperty(String text) {
            return text == null || !text.startsWith("--") ? null : propertyString(text.substring(2));
        }

        private String defaultValue(List<? extends ExpressionTree> args, boolean isGetProperty, boolean hasNoDefault) {
            if (hasNoDefault || args.size() <= 1) {
                return null;
            }
            return literalValue(args.get(isGetProperty && args.size() > 2 ? 2 : 1));
        }

        private void readPlaceholders(ExpressionTree tree) {
            if (tree instanceof NewArrayTree array) {
                array.getInitializers().forEach(this::readPlaceholders);
                return;
            }
            var text = literal(tree);
            if (text != null) {
                addPlaceholder(text, tree);
                addSpelReferences(text, tree);
            }
        }

        private void addSpelReferences(String text, ExpressionTree tree) {
            addSpelReferences(text, tree, FindingRole.READ, "spel-value");
        }

        private void expandSpringApplicationJson(String key, String rawValue, com.sun.source.tree.Tree tree) {
            if (rawValue == null || !key.equals("SPRING_APPLICATION_JSON") && !key.equals("spring.application.json")) {
                return;
            }
            try {
                flattenSpringApplicationJson(YAML_READER.readValue(rawValue), "", tree);
            } catch (Exception ignored) {
                // ponytail: keep raw property; add detector diagnostics if malformed source JSON matters.
            }
        }

        private void flattenSpringApplicationJson(Object node, String prefix, com.sun.source.tree.Tree tree) {
            if (node instanceof Map<?, ?> map) {
                for (var entry : map.entrySet()) {
                    var key = String.valueOf(entry.getKey());
                    flattenSpringApplicationJson(entry.getValue(), prefix.isBlank() ? key : prefix + "." + key, tree);
                }
                return;
            }
            if (node instanceof List<?> list) {
                for (var index = 0; index < list.size(); index++) {
                    flattenSpringApplicationJson(list.get(index), prefix + "[" + index + "]", tree);
                }
                return;
            }
            if (!prefix.isBlank() && node != null) {
                var value = String.valueOf(node);
                findings.add(new ConfigFinding(
                    prefix,
                    prefix,
                    FindingRole.DEFINE,
                    new ConfigValue(value, value, typeOf(value)),
                    null,
                    EnvironmentContext.none(),
                    source(tree, SourceKind.JAVA),
                    Confidence.HIGH,
                    id(),
                    new ExternalDetails("spring", "application-json", null)
                ));
            }
        }

        private void addSpelReferences(
            String text,
            com.sun.source.tree.Tree tree,
            FindingRole role,
            String detailSource
        ) {
            addSpelReferences(text, tree, SPEL_ENVIRONMENT, role, detailSource);
            addSpelReferences(text, tree, SPEL_SYSTEM_ENVIRONMENT, role, detailSource);
            addSpelReferences(text, tree, SPEL_SYSTEM_PROPERTIES, role, detailSource);
            addSpelReferences(text, tree, SPEL_ENVIRONMENT_GET_PROPERTY, role, detailSource);
            addSpelReferences(text, tree, SPEL_SYSTEM_ENVIRONMENT_GET, role, detailSource);
            addSpelReferences(text, tree, SPEL_SYSTEM_PROPERTIES_GET_PROPERTY, role, detailSource);
            addSpelReferences(text, tree, SPEL_SYSTEM_PROPERTIES_GET, role, detailSource);
        }

        private void addSpelReferences(
            String text,
            com.sun.source.tree.Tree tree,
            Pattern pattern,
            FindingRole role,
            String detailSource
        ) {
            var matcher = pattern.matcher(text);
            while (matcher.find()) {
                var key = matcher.group(1);
                findings.add(new ConfigFinding(
                    key,
                    key,
                    role,
                    null,
                    null,
                    EnvironmentContext.none(),
                    source(tree, SourceKind.JAVA),
                    Confidence.HIGH,
                    id(),
                    new ExternalDetails("spring", detailSource, null)
                ));
            }
        }

        private ConfigFinding externalFinding(
            String ruleId,
            String key,
            String value,
            String defaultValue,
            Confidence confidence,
            FindingRole role,
            com.sun.source.tree.Tree tree
        ) {
            return new ConfigFinding(
                key,
                key,
                role,
                value == null ? null : new ConfigValue(value, value, typeOf(value)),
                defaultValue == null ? null : new ConfigValue(defaultValue, defaultValue, typeOf(defaultValue)),
                EnvironmentContext.none(),
                source(tree, SourceKind.JAVA),
                confidence,
                id(),
                new ExternalDetails("rule", ruleId == null ? "unknown" : ruleId, null)
            );
        }

        private boolean matches(MethodCallRule rule, String method) {
            if (rule.method() == null || !method.endsWith("." + rule.method()) && !method.equals(rule.method())) {
                return false;
            }
            return rule.owner() == null || method.contains(simple(rule.owner()));
        }

        private String annotationValue(AnnotationTree annotation, String attribute) {
            var values = annotationValues(annotation, attribute);
            return values.isEmpty() ? null : values.getFirst();
        }

        private List<String> annotationValues(AnnotationTree annotation, String attribute) {
            var wanted = attribute == null ? "value" : attribute;
            for (var argument : annotation.getArguments()) {
                ExpressionTree expression = null;
                if (argument instanceof AssignmentTree assignment) {
                    if (assignment.getVariable().toString().equals(wanted)) {
                        expression = assignment.getExpression();
                    }
                } else if (wanted.equals("value")) {
                    expression = argument;
                }
                if (expression instanceof NewArrayTree array) {
                    return array.getInitializers().stream().map(this::literal).toList();
                }
                if (expression != null) {
                    var value = literal(expression);
                    return value == null ? List.of() : List.of(value);
                }
            }
            return List.of();
        }
    }

    private static String simple(String type) {
        if (type == null) {
            return "";
        }
        var dot = type.lastIndexOf('.');
        return dot < 0 ? type : type.substring(dot + 1);
    }

    private static ValueType typeOf(String value) {
        if (value == null) {
            return ValueType.UNKNOWN;
        }
        var text = value.trim();
        if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false")) {
            return ValueType.BOOLEAN;
        }
        if (text.matches("-?\\d+")) {
            return ValueType.INTEGER;
        }
        if (text.matches("(?i)-?\\d+(ns|us|ms|s|m|h|d)")
            || text.matches("(?i)(P\\d+D|P(?:\\d+D)?T(?=.*\\d)(?:\\d+H)?(?:\\d+M)?(?:\\d+(?:\\.\\d+)?S)?)")) {
            return ValueType.DURATION;
        }
        return ValueType.STRING;
    }

    private record PropertyPair(String key, String value) {
    }

    private static final class SourceFile extends SimpleJavaFileObject {
        private final String source;

        private SourceFile(java.net.URI uri, String source) {
            super(uri, Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return new String(source.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        }
    }
}
