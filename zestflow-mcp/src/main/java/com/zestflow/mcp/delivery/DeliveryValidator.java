package com.zestflow.mcp.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 平台交付门禁 — 汇总链拓扑、Pattern、Acceptance、反模式与 JavaDoc 评分。
 * <p>
 * 对标报告 {@code validate_delivery(project, appCode, strictMode)}。
 */
public class DeliveryValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DeliveryDod dod;

    public DeliveryValidator() {
        this(DeliveryDod.defaults());
    }

    public DeliveryValidator(DeliveryDod dod) {
        this.dod = dod;
    }

    public DeliveryReport validate(Path projectRoot, String appCode, boolean strictMode) throws IOException {
        List<String> blocking = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> nextActions = new ArrayList<>();

        Set<String> chainKeys = ProjectSourceAnalyzer.scanZestChainKeys(projectRoot);
        List<ProjectSourceAnalyzer.ExecuteMethodInfo> executeMethods =
                ProjectSourceAnalyzer.scanExecuteMethods(projectRoot);

        int patternCount = ProjectSourceAnalyzer.countMarkdownPatterns(projectRoot);
        if (patternCount == 0 && dod.requirePatterns()) {
            String code = strictMode ? "PATTERNS_MISSING" : "PATTERNS_EMPTY";
            (strictMode ? blocking : warnings).add(code + ": .zestflow/patterns/ 无项目 Pattern");
            nextActions.add("search_patterns → record_learning_event → distill_patterns");
        }

        Path journeysFile = projectRoot.resolve(".zestflow/acceptance/journeys.yml");
        boolean hasJourneys = Files.isRegularFile(journeysFile);
        if (!hasJourneys && dod.requireAcceptanceJourneys()) {
            blocking.add("ACCEPTANCE_MISSING: .zestflow/acceptance/journeys.yml");
            nextActions.add("gen_smoke_suite");
        }

        Path lastRun = projectRoot.resolve(".zestflow/acceptance/last-run.json");
        double smokePassRate = 1.0;
        if (hasJourneys && Files.isRegularFile(lastRun)) {
            smokePassRate = readSmokePassRate(lastRun);
            if (smokePassRate < 1.0) {
                warnings.add("SMOKE_PARTIAL: last-run passRate=" + String.format(Locale.ROOT, "%.2f", smokePassRate));
                nextActions.add("run_acceptance_suite");
            }
        } else if (hasJourneys) {
            warnings.add("SMOKE_NEVER_RUN: 存在 journeys.yml 但未执行 run_acceptance_suite");
            nextActions.add("run_acceptance_suite");
            smokePassRate = 0.0;
        }

        double topologyScore = evaluateTopology(projectRoot, executeMethods, blocking, warnings, strictMode);
        double playgroundScore = evaluatePlaygroundCoverage(projectRoot, chainKeys, warnings);
        double javadocScore = computeJavadocScore(executeMethods, warnings);

        if (dod.forbidAllInOne()) {
            for (ProjectSourceAnalyzer.ExecuteMethodInfo info : executeMethods) {
                if (info.lineCount() > dod.maxLinesPerExecute()) {
                    warnings.add("HANDLER_MONOLITH: "
                            + info.methodName() + " " + info.lineCount() + " lines @ "
                            + info.relativePath());
                }
            }
            if (executeMethods.stream().anyMatch(m -> m.lineCount() > dod.maxLinesPerExecute())) {
                nextActions.add("compose_chain（按 Pattern 拆分单体 Handler）");
            }
        }

        if (chainKeys.isEmpty()) {
            warnings.add("NO_ZEST_CHAIN: 未扫描到 @ZestChain 声明（Mode3 项目应有 Controller 绑链）");
        }

        double usableScore = 0.35 * topologyScore
                + 0.25 * playgroundScore
                + 0.30 * smokePassRate
                + 0.10 * javadocScore;

        boolean passed = blocking.isEmpty()
                && (!strictMode || usableScore >= dod.minUsableScore());

        if (passed && nextActions.isEmpty()) {
            nextActions.add("record_learning_event → distill_patterns");
        }

        return new DeliveryReport(
                passed,
                round(usableScore),
                chainKeys.size(),
                blocking,
                warnings,
                dedupe(nextActions));
    }

    private double evaluateTopology(Path projectRoot,
                                    List<ProjectSourceAnalyzer.ExecuteMethodInfo> executeMethods,
                                    List<String> blocking,
                                    List<String> warnings,
                                    boolean strictMode) throws IOException {
        Path chainsDir = projectRoot.resolve(".zestflow/chains");
        if (!Files.isDirectory(chainsDir)) {
            if (strictMode && executeMethods.size() > 0) {
                warnings.add("CHAIN_FILES_MISSING: 无 .zestflow/chains/ 生产链 JSON，请 compose_chain 后落盘");
            }
            return executeMethods.isEmpty() ? 1.0 : 0.5;
        }

        List<Path> chainFiles = Files.list(chainsDir)
                .filter(p -> p.getFileName().toString().endsWith(".json"))
                .toList();
        if (chainFiles.isEmpty()) {
            warnings.add("CHAIN_FILES_EMPTY: .zestflow/chains/ 无链定义 JSON");
            return 0.4;
        }

        int pass = 0;
        for (Path file : chainFiles) {
            String json = Files.readString(file);
            if (isConnectedProductionChain(json)) {
                pass++;
            } else if (json.contains("\"lifecycle\"") && json.contains("bootstrap")) {
                warnings.add("BOOTSTRAP_CHAIN: " + file.getFileName() + "（占位链，功能交付须 compose production）");
                pass++;
            } else {
                blocking.add("CHAIN_GRAPH_INVALID: " + file.getFileName());
            }
        }
        return chainFiles.isEmpty() ? 0.0 : (double) pass / chainFiles.size();
    }

    static boolean isConnectedProductionChain(String chainJson) {
        try {
            var root = MAPPER.readTree(chainJson);
            var nodes = root.path("nodes");
            var edges = root.path("edges");
            if (!nodes.isArray() || nodes.size() < 3) {
                return false;
            }
            if (!edges.isArray() || edges.isEmpty()) {
                return false;
            }
            int businessNodes = 0;
            for (var node : nodes) {
                String type = node.path("type").asText("NORMAL").toUpperCase(Locale.ROOT);
                if (!"START".equals(type) && !"END".equals(type)) {
                    businessNodes++;
                }
            }
            String lifecycle = root.path("config").path("lifecycle").asText("");
            if ("production".equalsIgnoreCase(lifecycle)) {
                return businessNodes >= 2;
            }
            return businessNodes >= 1 && !edges.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private double evaluatePlaygroundCoverage(Path projectRoot, Set<String> chainKeys, List<String> warnings) {
        Path scenesDir = projectRoot.resolve(".zestflow/playground");
        if (!Files.isDirectory(scenesDir)) {
            if (!chainKeys.isEmpty()) {
                warnings.add("PLAYGROUND_SCENES_MISSING: 无 .zestflow/playground/ 场景");
            }
            return chainKeys.isEmpty() ? 1.0 : 0.3;
        }
        try {
            long sceneFiles = Files.list(scenesDir)
                    .filter(p -> p.getFileName().toString().endsWith(".json")
                            || p.getFileName().toString().endsWith(".yml"))
                    .count();
            if (chainKeys.isEmpty()) {
                return 1.0;
            }
            double ratio = Math.min(1.0, (double) sceneFiles / Math.max(1, chainKeys.size()));
            if (ratio < 0.5) {
                warnings.add("PLAYGROUND_LOW_COVERAGE: scenes=" + sceneFiles + " chainKeys=" + chainKeys.size());
            }
            return ratio;
        } catch (IOException e) {
            return 0.3;
        }
    }

    private static double computeJavadocScore(List<ProjectSourceAnalyzer.ExecuteMethodInfo> methods,
                                              List<String> warnings) {
        if (methods.isEmpty()) {
            return 1.0;
        }
        long documented = methods.stream().filter(ProjectSourceAnalyzer.ExecuteMethodInfo::hasJavaDoc).count();
        double score = (double) documented / methods.size();
        if (score < 0.9) {
            warnings.add("JAVADOC_INCOMPLETE: " + documented + "/" + methods.size());
        }
        return score;
    }

    private static double readSmokePassRate(Path lastRun) {
        try {
            var root = MAPPER.readTree(Files.readString(lastRun));
            if (root.has("passRate")) {
                return root.get("passRate").asDouble(0.0);
            }
            int total = root.path("total").asInt(0);
            int passed = root.path("passed").asInt(0);
            return total == 0 ? 0.0 : (double) passed / total;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static List<String> dedupe(List<String> items) {
        return new ArrayList<>(new LinkedHashSet<>(items));
    }
}
