package io.github.hzzzzzx.configradar.core.export;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.ConfigInventory;
import io.github.hzzzzzx.configradar.core.model.ConfigValue;
import io.github.hzzzzzx.configradar.core.model.Diagnostic;
import io.github.hzzzzzx.configradar.core.model.SourceLocation;
import io.github.hzzzzzx.configradar.core.model.UncertainFinding;
import io.github.hzzzzzx.configradar.core.output.ConsumerContext;
import io.github.hzzzzzx.configradar.core.output.ConsumerSink;
import io.github.hzzzzzx.configradar.core.output.InventoryConsumer;
import io.github.hzzzzzx.configradar.core.scan.RedactionPolicy;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@link InventoryConsumer} that renders a single self-contained HTML report ({@code
 * config-report.html}) for human review and quick demos.
 *
 * <p>The output has no external dependencies (no CDN, no JS framework): collapsible sections use the
 * native {@code <details>}/{@code <summary>} elements, the per-key detail popup uses the native
 * {@code <dialog>} element, and charts are pure CSS ({@code conic-gradient} donut, flex bars). The
 * only script is a small vanilla-JS row filter plus modal open/close. This keeps the file portable —
 * open it offline, attach it to a ticket, screenshot it.
 *
 * <p>Each config key collapses to ONE row showing the value that wins by Spring externalized-config
 * priority (reusing {@link AppConfigCenterExporter#springPriority}); every occurrence (all sources,
 * all values) is shown in the per-key popup.
 *
 * <p>All scanned text (keys, values, paths) is HTML-escaped before embedding; values come from
 * arbitrary source code and must never break out of the document.
 */
public final class HtmlReportConsumer implements InventoryConsumer {

    private static final RedactionPolicy SENSITIVE = RedactionPolicy.redactSensitive();
    private static final int TOP_DIRS = 8;

    @Override
    public String id() {
        return "html";
    }

    @Override
    public void consume(ConfigInventory inventory, ConsumerContext context, ConsumerSink sink) throws Exception {
        var html = render(inventory);
        try (var out = sink.openFile("config-report.html");
             var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            writer.write(html);
        }
    }

    private String render(ConfigInventory inventory) {
        var items = inventory.items();
        var uncertain = inventory.uncertain();
        var checks = inventory.checks();
        var diagnostics = inventory.diagnostics();

        // One group per key, each sorted so the Spring-priority winner is first.
        var groups = new LinkedHashMap<String, List<ConfigFinding>>();
        items.stream()
            .sorted(Comparator.comparing(ConfigFinding::normalizedKey))
            .forEach(f -> groups.computeIfAbsent(f.normalizedKey(), k -> new ArrayList<>()).add(f));
        for (var g : groups.values()) {
            g.sort(Comparator.comparingInt(AppConfigCenterExporter::springPriority).reversed());
        }

        long sensitiveKeys = groups.keySet().stream().filter(SENSITIVE::matchesKey).count();
        long multiSource = groups.values().stream().filter(g -> g.size() > 1).count();
        var byConfidence = countBy(items, f -> f.confidence().name());
        var byRole = countBy(items, f -> f.role().name());
        var bySourceKind = countBy(items, f -> f.source().sourceKind().name());
        var dirStats = directoryStats(items);

        var project = inventory.project();
        var sb = new StringBuilder(96 * 1024);
        sb.append("<!DOCTYPE html>\n<html lang=\"zh\">\n<head>\n<meta charset=\"utf-8\">\n")
            .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
            .append("<title>ConfigRadar 配置报告 — ").append(esc(project.name())).append("</title>\n")
            .append("<style>").append(CSS).append("</style>\n</head>\n<body>\n");

        // Header.
        sb.append("<header>\n<h1>ConfigRadar 配置报告</h1>\n<div class=\"sub\">")
            .append("<span class=\"proj\">").append(esc(project.name())).append("</span>")
            .append(" <span class=\"ref\">").append(esc(project.ref())).append("</span>")
            .append(" <span class=\"schema\">").append(esc(inventory.schemaVersion())).append("</span>")
            .append("</div>\n</header>\n");

        // 说明 + 图例。
        sb.append("<p class=\"intro\">本报告由 ConfigRadar 扫描项目源码自动生成。每个配置键只展示一条——")
            .append("按 Spring 加载优先级选出的生效值；点击「详情」可在弹窗中查看该键的所有来源与取值。</p>\n");
        sb.append("<details class=\"help\">\n<summary>如何阅读本报告</summary>\n<ul>\n")
            .append("<li><b>生效值</b>：按 Spring 外部化配置优先级选出（.env &gt; application-{profile} &gt; application &gt; bootstrap &gt; 代码）。</li>\n")
            .append("<li><b>置信度</b>：<span class=\"conf conf-high\">高</span> 静态可确定，")
            .append("<span class=\"conf conf-medium\">中</span> 需结合上下文，")
            .append("<span class=\"conf conf-low\">低</span> 仅启发式推断。</li>\n")
            .append("<li><b>角色</b>：定义=声明默认值，读取=代码读取该键，条件=用于条件判断，元数据=框架元信息。</li>\n")
            .append("<li><span class=\"lock\">🔒</span> 标记敏感键（password、secret、token 等）；「出现」列为该键在源码中被发现的次数。</li>\n")
            .append("</ul>\n</details>\n");

        // Stat cards.
        sb.append("<section class=\"stats\">\n");
        statCard(sb, groups.size(), "配置项");
        statCard(sb, items.size(), "发现");
        statCard(sb, multiSource, "多来源键");
        statCard(sb, sensitiveKeys, "敏感键");
        statCard(sb, uncertain.size(), "不确定");
        statCard(sb, dirStats.size(), "涉及目录");
        statCard(sb, diagnostics.size(), "诊断");
        sb.append("</section>\n");

        // Charts: confidence donut + role/source bars.
        sb.append("<section class=\"charts\">\n");
        donut(sb, "置信度分布", byConfidence, items.size());
        barCard(sb, "角色分布", byRole, items.size());
        barCard(sb, "来源类型", bySourceKind, items.size());
        sb.append("</section>\n");

        // 目录分布（折叠）：top-N 排行条形图 + 完整表格。
        renderDirectorySection(sb, dirStats, items.size());

        // Search.
        sb.append("<div class=\"search\"><input id=\"q\" type=\"search\" placeholder=\"按键名、取值或文件路径过滤…\" ")
            .append("oninput=\"filterRows(this.value)\"></div>\n");

        // 配置清单：每个键一行，详情在弹窗。
        sb.append("<details open>\n<summary>配置清单 <span class=\"n\">")
            .append(groups.size()).append(" 个键 / ").append(items.size()).append(" 条发现</span></summary>\n");
        sb.append("<table>\n<thead><tr><th>键</th><th>生效值</th><th>角色</th><th>置信度</th>")
            .append("<th>Profile</th><th>出现</th><th></th></tr></thead>\n<tbody>\n");
        int idx = 0;
        var templates = new StringBuilder();
        for (var group : groups.values()) {
            keyRow(sb, templates, group, idx++);
        }
        sb.append("</tbody>\n</table>\n</details>\n");

        // Uncertain.
        if (!uncertain.isEmpty()) {
            sb.append("<details>\n<summary>不确定与动态 <span class=\"n\">")
                .append(uncertain.size()).append("</span></summary>\n");
            sb.append("<table>\n<thead><tr><th>表达式</th><th>原因</th><th>置信度</th>")
                .append("<th>来源</th></tr></thead>\n<tbody>\n");
            for (var u : uncertain) {
                uncertainRow(sb, u);
            }
            sb.append("</tbody>\n</table>\n</details>\n");
        }

        // Diagnostics.
        if (!diagnostics.isEmpty()) {
            sb.append("<details>\n<summary>诊断 <span class=\"n\">")
                .append(diagnostics.size()).append("</span></summary>\n");
            sb.append("<table>\n<thead><tr><th>级别</th><th>阶段</th><th>信息</th>")
                .append("<th>组件</th></tr></thead>\n<tbody>\n");
            for (var d : diagnostics) {
                diagnosticRow(sb, d);
            }
            sb.append("</tbody>\n</table>\n</details>\n");
        }

        // Per-key detail templates (inert <template>, not rendered until opened) + the shared modal.
        sb.append(templates);
        sb.append("<dialog id=\"modal\"><div class=\"modal-head\"><span id=\"modal-title\"></span>")
            .append("<button class=\"x\" onclick=\"closeModal()\">关闭 ✕</button></div>")
            .append("<div id=\"modal-body\"></div></dialog>\n");

        sb.append("<footer>由 ConfigRadar 生成 — 可用任意浏览器直接打开本文件。</footer>\n");
        sb.append("<script>").append(JS).append("</script>\n</body>\n</html>\n");
        return sb.toString();
    }

    /** Renders the collapsed one-row-per-key entry, and emits its detail into {@code templates}. */
    private static void keyRow(StringBuilder sb, StringBuilder templates, List<ConfigFinding> group, int idx) {
        var winner = group.get(0);
        var key = winner.normalizedKey();
        boolean isSecret = SENSITIVE.matchesKey(winner.key());
        var value = displayValue(winner.value(), winner.defaultValue());

        // data-s carries every value/path so a filter on any source still matches the key.
        var search = new StringBuilder(key.toLowerCase());
        for (var f : group) {
            search.append(' ').append(displayValue(f.value(), f.defaultValue()).toLowerCase())
                .append(' ').append(f.source().path().toLowerCase());
        }
        sb.append("<tr data-s=\"").append(esc(search.toString())).append("\">");
        sb.append("<td class=\"key\">").append(esc(key));
        if (isSecret) {
            sb.append(" <span class=\"lock\" title=\"敏感键\">🔒</span>");
        }
        sb.append("</td>");
        sb.append("<td class=\"val\">").append(esc(value)).append("</td>");
        sb.append("<td>").append(esc(role(winner.role().name()))).append("</td>");
        sb.append("<td>").append(confBadge(winner.confidence().name())).append("</td>");
        sb.append("<td>").append(esc(orDash(winner.environment().profile()))).append("</td>");
        sb.append("<td class=\"cnt\">").append(group.size()).append("</td>");
        sb.append("<td><button class=\"link\" onclick=\"openModal('d").append(idx)
            .append("')\">详情</button></td>");
        sb.append("</tr>\n");

        // Detail popup: every occurrence, winner first and marked.
        templates.append("<template id=\"d").append(idx).append("\">");
        templates.append("<div class=\"mkey\">").append(esc(key));
        if (isSecret) {
            templates.append(" <span class=\"lock\">🔒</span>");
        }
        templates.append("</div>");
        templates.append("<table class=\"mtable\"><thead><tr><th></th><th>值</th><th>角色</th>")
            .append("<th>置信度</th><th>来源</th><th>Profile</th></tr></thead><tbody>");
        for (int i = 0; i < group.size(); i++) {
            var f = group.get(i);
            templates.append("<tr>");
            templates.append("<td>").append(i == 0 ? "<span class=\"win\" title=\"生效值\">★</span>" : "").append("</td>");
            templates.append("<td class=\"val\">").append(esc(displayValue(f.value(), f.defaultValue()))).append("</td>");
            templates.append("<td>").append(esc(role(f.role().name()))).append("</td>");
            templates.append("<td>").append(confBadge(f.confidence().name())).append("</td>");
            templates.append("<td class=\"src\">").append(source(f.source())).append("</td>");
            templates.append("<td>").append(esc(orDash(f.environment().profile()))).append("</td>");
            templates.append("</tr>");
        }
        templates.append("</tbody></table></template>\n");
    }

    private static void uncertainRow(StringBuilder sb, UncertainFinding u) {
        var search = (u.expression() + " " + u.source().path()).toLowerCase();
        sb.append("<tr data-s=\"").append(esc(search)).append("\">");
        sb.append("<td class=\"key\">").append(esc(u.expression())).append("</td>");
        sb.append("<td>").append(esc(u.reason().name())).append("</td>");
        sb.append("<td>").append(confBadge(u.confidence().name())).append("</td>");
        sb.append("<td class=\"src\">").append(source(u.source())).append("</td>");
        sb.append("</tr>\n");
    }

    private static void diagnosticRow(StringBuilder sb, Diagnostic d) {
        var search = (orDash(d.message()) + " " + orDash(d.phase())).toLowerCase();
        sb.append("<tr data-s=\"").append(esc(search)).append("\">");
        sb.append("<td>").append(esc(String.valueOf(d.severity()))).append("</td>");
        sb.append("<td>").append(esc(orDash(d.phase()))).append("</td>");
        sb.append("<td>").append(esc(orDash(d.message()))).append("</td>");
        sb.append("<td>").append(esc(orDash(d.componentId()))).append("</td>");
        sb.append("</tr>\n");
    }

    // ---- charts & stats ----

    private static void statCard(StringBuilder sb, long n, String label) {
        sb.append("<div class=\"card\"><div class=\"num\">").append(n)
            .append("</div><div class=\"lbl\">").append(esc(label)).append("</div></div>\n");
    }

    /** Pure-CSS donut via conic-gradient; the hole is a radial mask, total count in the centre. */
    private static void donut(StringBuilder sb, String title, Map<String, Long> counts, int total) {
        sb.append("<div class=\"chart\"><div class=\"chart-h\">").append(esc(title)).append("</div>");
        var gradient = new StringBuilder();
        double acc = 0;
        for (var e : counts.entrySet()) {
            double start = total > 0 ? acc / total * 360 : 0;
            acc += e.getValue();
            double end = total > 0 ? acc / total * 360 : 0;
            if (gradient.length() > 0) {
                gradient.append(',');
            }
            gradient.append("var(--c-").append(esc(e.getKey().toLowerCase())).append(") ")
                .append(fmt(start)).append("deg ").append(fmt(end)).append("deg");
        }
        if (gradient.length() == 0) {
            gradient.append("#eaeef2 0deg 360deg");
        }
        sb.append("<div class=\"donutwrap\"><div class=\"donut\" style=\"background:conic-gradient(")
            .append(gradient).append(")\"><span class=\"donut-c\">").append(total).append("</span></div></div>");
        legend(sb, counts);
        sb.append("</div>\n");
    }

    private static void barCard(StringBuilder sb, String title, Map<String, Long> counts, int total) {
        sb.append("<div class=\"chart\"><div class=\"chart-h\">").append(esc(title)).append("</div>");
        sb.append("<div class=\"bar\">");
        if (total > 0) {
            for (var e : counts.entrySet()) {
                double pct = 100.0 * e.getValue() / total;
                sb.append("<span class=\"seg seg-").append(esc(e.getKey().toLowerCase()))
                    .append("\" style=\"width:").append(fmt(pct)).append("%\" title=\"")
                    .append(esc(e.getKey())).append(": ").append(e.getValue()).append("\"></span>");
            }
        }
        sb.append("</div>");
        legend(sb, counts);
        sb.append("</div>\n");
    }

    private static void legend(StringBuilder sb, Map<String, Long> counts) {
        sb.append("<div class=\"legend\">");
        for (var e : counts.entrySet()) {
            sb.append("<span class=\"li\"><i class=\"seg-").append(esc(e.getKey().toLowerCase()))
                .append("\"></i>").append(esc(e.getKey())).append(' ').append(e.getValue()).append("</span>");
        }
        sb.append("</div>");
    }

    private static void renderDirectorySection(StringBuilder sb, Map<String, DirStat> dirStats, int total) {
        var ranked = dirStats.entrySet().stream()
            .sorted(Comparator.comparingInt((Map.Entry<String, DirStat> e) -> e.getValue().findings).reversed())
            .toList();
        int max = ranked.stream().mapToInt(e -> e.getValue().findings).max().orElse(1);
        sb.append("<details open>\n<summary>目录 / 模块分布 <span class=\"n\">")
            .append(dirStats.size()).append(" 个目录</span></summary>\n");

        // Top-N horizontal ranking bars.
        sb.append("<div class=\"ranks\">\n");
        for (var e : ranked.stream().limit(TOP_DIRS).toList()) {
            double pct = 100.0 * e.getValue().findings / max;
            sb.append("<div class=\"rank\"><span class=\"rl\" title=\"").append(esc(e.getKey())).append("\">")
                .append(esc(e.getKey())).append("</span>")
                .append("<span class=\"rb\"><i style=\"width:").append(fmt(pct)).append("%\"></i></span>")
                .append("<span class=\"rn\">").append(e.getValue().findings).append("</span></div>\n");
        }
        sb.append("</div>\n");

        // Full table.
        sb.append("<table>\n<thead><tr><th>目录</th><th>键数</th><th>发现数</th><th>敏感</th></tr></thead>\n<tbody>\n");
        for (var e : ranked) {
            var s = e.getValue();
            sb.append("<tr><td class=\"src\">").append(esc(e.getKey())).append("</td>")
                .append("<td class=\"cnt\">").append(s.keys.size()).append("</td>")
                .append("<td class=\"cnt\">").append(s.findings).append("</td>")
                .append("<td class=\"cnt\">").append(s.sensitive == 0 ? "—" : String.valueOf(s.sensitive))
                .append("</td></tr>\n");
        }
        sb.append("</tbody>\n</table>\n</details>\n");
    }

    private static Map<String, DirStat> directoryStats(List<ConfigFinding> items) {
        var stats = new LinkedHashMap<String, DirStat>();
        for (var f : items) {
            var dir = directoryOf(f.source().path());
            var s = stats.computeIfAbsent(dir, k -> new DirStat());
            s.findings++;
            s.keys.add(f.normalizedKey());
            if (SENSITIVE.matchesKey(f.key())) {
                s.sensitive++;
            }
        }
        return stats;
    }

    private static String directoryOf(String path) {
        if (path == null || path.isEmpty()) {
            return "(根目录)";
        }
        int slash = path.lastIndexOf('/');
        return slash <= 0 ? "(根目录)" : path.substring(0, slash);
    }

    /** Mutable per-directory aggregate. */
    private static final class DirStat {
        int findings;
        int sensitive;
        final java.util.Set<String> keys = new java.util.HashSet<>();
    }

    // ---- small cell helpers ----

    private static String source(SourceLocation s) {
        var path = esc(s.path());
        return s.line() == null ? path : path + ":<span class=\"ln\">" + s.line() + "</span>";
    }

    private static String confBadge(String conf) {
        var label = switch (conf) {
            case "HIGH" -> "高";
            case "MEDIUM" -> "中";
            case "LOW" -> "低";
            default -> conf;
        };
        return "<span class=\"conf conf-" + esc(conf.toLowerCase()) + "\" title=\"" + esc(conf) + "\">"
            + esc(label) + "</span>";
    }

    private static String role(String role) {
        return switch (role) {
            case "DEFINE" -> "定义";
            case "READ" -> "读取";
            case "CONDITION" -> "条件";
            case "METADATA" -> "元数据";
            default -> role;
        };
    }

    private static String displayValue(ConfigValue value, ConfigValue defaultValue) {
        if (value != null && value.raw() != null && !value.raw().isEmpty()) {
            return value.raw();
        }
        if (defaultValue != null && defaultValue.raw() != null && !defaultValue.raw().isEmpty()) {
            return defaultValue.raw() + "（默认值）";
        }
        return "—";
    }

    private static <T> Map<String, Long> countBy(List<T> items, java.util.function.Function<T, String> key) {
        var counts = new LinkedHashMap<String, Long>();
        for (var item : items) {
            counts.merge(key.apply(item), 1L, Long::sum);
        }
        return counts;
    }

    private static String orDash(String s) {
        return s == null || s.isEmpty() ? "—" : s;
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.1f", d);
    }

    /** HTML-escapes text before embedding. Security boundary: scanned values are untrusted. */
    static String esc(String s) {
        if (s == null) {
            return "";
        }
        var out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static final String CSS = """
        :root{--bg:#f6f7f9;--card:#fff;--ink:#1f2328;--mut:#656d76;--line:#d8dee4;--accent:#0969da;\
        --c-high:#1a7f37;--c-medium:#bf8700;--c-low:#cf222e;\
        --c-define:#8250df;--c-read:#0969da;--c-condition:#bf8700;--c-metadata:#656d76;--c-unknown:#afb8c1}\
        *{box-sizing:border-box}\
        body{margin:0;font:14px/1.5 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,"PingFang SC","Microsoft YaHei",Helvetica,Arial,sans-serif;\
        color:var(--ink);background:var(--bg)}\
        header{background:var(--card);border-bottom:1px solid var(--line);padding:18px 24px}\
        h1{margin:0;font-size:20px}\
        .sub{margin-top:4px;color:var(--mut);font-size:13px}\
        .sub .proj{font-weight:600;color:var(--ink)}\
        .sub .ref,.sub .schema{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:12px}\
        section,.search,details,footer,.intro{max-width:1100px;margin:16px auto;padding:0 24px}\
        .intro{margin:8px auto 0;color:var(--mut);line-height:1.7}\
        details.help{margin-top:8px}\
        details.help ul{margin:6px 0 0;padding-left:20px;color:var(--mut);line-height:1.8}\
        details.help li b{color:var(--ink)}\
        .stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(130px,1fr));gap:12px}\
        .card{background:var(--card);border:1px solid var(--line);border-radius:8px;padding:14px 16px}\
        .card .num{font-size:26px;font-weight:700}\
        .card .lbl{color:var(--mut);font-size:12px;letter-spacing:.04em}\
        .charts{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:16px}\
        .chart{background:var(--card);border:1px solid var(--line);border-radius:8px;padding:14px 16px}\
        .chart-h{font-size:13px;font-weight:600;margin-bottom:12px}\
        .donutwrap{display:flex;justify-content:center;margin:4px 0 12px}\
        .donut{width:130px;height:130px;border-radius:50%;display:flex;align-items:center;justify-content:center;\
        -webkit-mask:radial-gradient(circle,transparent 54%,#000 55%);mask:radial-gradient(circle,transparent 54%,#000 55%)}\
        .donut-c{font-size:24px;font-weight:700;color:var(--ink)}\
        .bar{display:flex;height:16px;border-radius:8px;overflow:hidden;background:#eaeef2;margin:18px 0 12px}\
        .seg{display:block;height:100%}\
        .legend{display:flex;flex-wrap:wrap;gap:10px;font-size:12px;color:var(--mut)}\
        .legend i{display:inline-block;width:10px;height:10px;border-radius:2px;margin-right:4px;vertical-align:middle}\
        .seg-high,i.seg-high{background:var(--c-high)}.seg-medium,i.seg-medium{background:var(--c-medium)}\
        .seg-low,i.seg-low{background:var(--c-low)}\
        .seg-define,i.seg-define{background:var(--c-define)}.seg-read,i.seg-read{background:var(--c-read)}\
        .seg-condition,i.seg-condition{background:var(--c-condition)}.seg-metadata,i.seg-metadata{background:var(--c-metadata)}\
        .seg-unknown,i.seg-unknown{background:var(--c-unknown)}\
        .seg-properties_file,i.seg-properties_file{background:#0969da}.seg-yaml_file,i.seg-yaml_file{background:#1a7f37}\
        .seg-java,i.seg-java{background:#8250df}.seg-java_source,i.seg-java_source{background:#8250df}\
        .seg-annotation,i.seg-annotation{background:#bf8700}\
        .ranks{display:flex;flex-direction:column;gap:8px;margin:6px 0 16px}\
        .rank{display:grid;grid-template-columns:minmax(120px,260px) 1fr 44px;gap:10px;align-items:center;font-size:12px}\
        .rl{color:var(--mut);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;font-family:ui-monospace,Menlo,monospace}\
        .rb{background:#eaeef2;border-radius:6px;height:12px;overflow:hidden}\
        .rb i{display:block;height:100%;background:var(--accent);border-radius:6px}\
        .rn{text-align:right;color:var(--ink);font-weight:600}\
        .search input{width:100%;padding:10px 14px;border:1px solid var(--line);border-radius:8px;font-size:14px;background:var(--card)}\
        summary{cursor:pointer;font-size:15px;font-weight:600;padding:10px 0;user-select:none}\
        summary .n{display:inline-block;background:#eaeef2;color:var(--mut);border-radius:10px;padding:0 8px;font-size:12px;margin-left:6px}\
        table{width:100%;border-collapse:collapse;background:var(--card);border:1px solid var(--line);border-radius:8px;overflow:hidden}\
        th,td{text-align:left;padding:8px 12px;border-bottom:1px solid var(--line);vertical-align:top}\
        th{background:#f6f8fa;font-size:12px;letter-spacing:.03em;color:var(--mut);position:sticky;top:0}\
        tr:last-child td{border-bottom:none}\
        td.key{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:13px;word-break:break-all}\
        td.val{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:13px;color:#0a3069;word-break:break-all;max-width:340px}\
        td.src{color:var(--mut);font-family:ui-monospace,Menlo,monospace;font-size:12px;word-break:break-all}\
        td.src .ln{color:var(--accent)}\
        td.cnt{text-align:right;font-variant-numeric:tabular-nums}\
        .conf{font-size:11px;font-weight:600;padding:2px 7px;border-radius:10px}\
        .conf-high{background:#dafbe1;color:var(--c-high)}.conf-medium{background:#fff8c5;color:var(--c-medium)}\
        .conf-low{background:#ffebe9;color:var(--c-low)}\
        .lock{font-size:12px}\
        button.link{border:1px solid var(--line);background:#f6f8fa;color:var(--accent);border-radius:6px;\
        padding:3px 10px;font-size:12px;cursor:pointer}button.link:hover{background:#eaeef2}\
        dialog{border:none;border-radius:12px;padding:0;max-width:760px;width:92%;box-shadow:0 12px 40px rgba(0,0,0,.25)}\
        dialog::backdrop{background:rgba(27,31,36,.5)}\
        .modal-head{display:flex;align-items:center;justify-content:space-between;padding:14px 18px;border-bottom:1px solid var(--line)}\
        #modal-title{font-weight:600}\
        button.x{border:none;background:none;color:var(--mut);cursor:pointer;font-size:13px}\
        #modal-body{padding:6px 18px 18px}\
        #modal-body .mkey{display:none}\
        .mtable{border:1px solid var(--line)}.mtable td,.mtable th{font-size:12px}\
        .win{color:var(--c-medium)}\
        footer{color:var(--mut);font-size:12px;text-align:center;padding:24px}\
        .hidden{display:none}\
        """;

    private static final String JS = """
        function filterRows(q){q=q.trim().toLowerCase();\
        document.querySelectorAll('[data-s]').forEach(function(el){\
        var s=el.getAttribute('data-s')||'';\
        el.classList.toggle('hidden',q!==''&&s.indexOf(q)===-1);});}\
        function openModal(id){var t=document.getElementById(id);if(!t)return;\
        var key=t.content.querySelector('.mkey');\
        document.getElementById('modal-title').textContent=key?key.textContent:'详情';\
        var b=document.getElementById('modal-body');b.innerHTML='';b.appendChild(t.content.cloneNode(true));\
        document.getElementById('modal').showModal();}\
        function closeModal(){document.getElementById('modal').close();}\
        document.addEventListener('click',function(e){var m=document.getElementById('modal');\
        if(m&&m.open&&e.target===m)m.close();});\
        """;
}
