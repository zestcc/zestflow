package com.zestflow.mcp.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zestflow.mcp.client.HttpApiClient;
import com.zestflow.mcp.config.McpRuntimeConfig;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * P1～P3 学习 Tool 门面 — 供 McpToolFactory 委托。
 */
public class LearningToolService {

    private final McpRuntimeConfig config;
    private final HttpApiClient apiClient;
    private final ObjectMapper mapper;
    private final LearningEventStore eventStore;
    private final PatternStore patternStore;
    private final PatternSearcher patternSearcher;
    private final ChainPlanService planService;
    private final PatternDistiller distiller;
    private final PlaygroundSceneGenerator sceneGenerator;

    public LearningToolService(McpRuntimeConfig config, HttpApiClient apiClient) {
        this.config = config;
        this.apiClient = apiClient;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.eventStore = new LearningEventStore(config.projectRoot());
        this.patternStore = new PatternStore(config.projectRoot());
        this.patternSearcher = new PatternSearcher(config.projectRoot());
        this.planService = new ChainPlanService(patternSearcher);
        this.distiller = new PatternDistiller(eventStore, patternStore);
        this.sceneGenerator = new PlaygroundSceneGenerator();
    }

    public String planChain(Map<String, Object> args) throws Exception {
        String description = stringArg(args, "description", stringArg(args, "userMessage", ""));
        String appCode = stringArg(args, "appCode", config.appCode());
        String componentsJson = apiClient.listComponents(appCode);
        Set<String> ids = ComponentRegistryParser.parseIds(componentsJson);
        ChainPlan plan = planService.plan(description, appCode, ids);
        List<PatternDocument> patterns = patternSearcher.search(description, 3);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("plan", plan);
        out.put("registeredComponentCount", ids.size());
        out.put("relatedPatterns", patterns.stream().map(p -> Map.of(
                "id", p.id(),
                "scope", p.scope(),
                "title", p.title(),
                "confidence", p.confidenceScore())).toList());
        out.put("intents", LearningWorkflow.INSTRUCTIONS);
        out.put("accuracyTarget", AccuracyGate.PROMOTION_SCORE_THRESHOLD);
        out.put("acceptanceRule", "ai-generation-acceptance：验收标准 + RAG 检索 + 自动蒸馏");
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(out);
    }

    public String recordLearningEvent(Map<String, Object> args) throws Exception {
        if (config.executorUrl() != null && !config.executorUrl().isBlank()) {
            Map<String, Object> body = new LinkedHashMap<>(args);
            body.putIfAbsent("appCode", config.appCode());
            String raw = apiClient.recordExecutorLearningEvent(body);
            return raw;
        }
        LearningEvent draft = new LearningEvent(
                stringArg(args, "id", UUID.randomUUID().toString()),
                Instant.now(),
                stringArg(args, "intent", null),
                stringArg(args, "feature", null),
                stringArg(args, "appCode", config.appCode()),
                stringArg(args, "chainCode", null),
                intArg(args, "httpMode", null),
                stringListArg(args, "reusedComponents"),
                stringListArg(args, "createdComponents"),
                intArg(args, "validateRounds", 1),
                booleanArg(args, "validatePassed", false),
                booleanArg(args, "adopted", false),
                booleanArg(args, "playgroundSuccess", false),
                stringArg(args, "userCorrection", null),
                stringArg(args, "chainData", null),
                null);
        AccuracyGate.GateResult gate = AccuracyGate.evaluate(draft);
        LearningEvent saved = eventStore.append(draft);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("saved", saved);
        out.put("promotionEligible", gate.passed());
        out.put("promotionScore", gate.score());
        out.put("promotionReason", gate.reason());
        out.put("eventsFile", eventStore.eventsFile().toString());
        out.put("storage", "local:.zestflow/learning");
        if (gate.passed()) {
            PatternDistiller.DistillResult distilled = distiller.distill(saved.feature());
            out.put("autoDistilled", true);
            out.put("distilledPatternCount", distilled.promotedCount());
        }
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(out);
    }

    public String searchPatterns(Map<String, Object> args) throws Exception {
        String q = stringArg(args, "query", stringArg(args, "q", ""));
        int limit = intArg(args, "limit", 5);
        if (config.executorUrl() != null && !config.executorUrl().isBlank()) {
            String raw = apiClient.searchExecutorRag(q, limit);
            JsonNode root = mapper.readTree(raw);
            JsonNode data = root.has("data") ? root.get("data") : root;
            List<Map<String, Object>> snippets = new ArrayList<>();
            if (data.isArray()) {
                for (JsonNode node : data) {
                    snippets.add(Map.of("scope", "executor", "preview", node.asText()));
                }
            }
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(snippets);
        }
        List<PatternDocument> hits = patternSearcher.search(q, limit);
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(hits.stream()
                .map(p -> Map.of(
                        "id", p.id(),
                        "scope", p.scope(),
                        "title", p.title(),
                        "feature", p.feature(),
                        "confidence", p.confidenceScore(),
                        "preview", p.markdown().lines().limit(8).reduce((a, b) -> a + "\n" + b).orElse("")))
                .toList());
    }

    public String distillPatterns(Map<String, Object> args) throws Exception {
        String feature = stringArg(args, "feature", null);
        if (config.executorUrl() != null && !config.executorUrl().isBlank()) {
            return apiClient.distillExecutorPatterns(feature);
        }
        PatternDistiller.DistillResult result = distiller.distill(feature);
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
    }

    public String genPlaygroundScene(Map<String, Object> args) throws Exception {
        String feature = stringArg(args, "feature", "feature");
        String chainCode = stringArg(args, "chainCode", "CHN_DEMO");
        int httpMode = intArg(args, "httpMode", 1);
        PlaygroundSceneDraft draft = sceneGenerator.generate(feature, chainCode, httpMode, null);
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(draft);
    }

    public String sharePattern(Map<String, Object> args) throws Exception {
        String patternId = stringArg(args, "patternId", null);
        if (patternId == null || patternId.isBlank()) {
            throw new IllegalArgumentException("patternId 不能为空");
        }
        String mode = stringArg(args, "mode", "team_export");
        if ("team_export".equalsIgnoreCase(mode)) {
            String json = patternStore.exportForTeamImport(patternId);
            Map<String, Object> wrap = new LinkedHashMap<>();
            wrap.put("adminImportHint", "POST /api/zestflow/ai/rag/documents/import");
            wrap.put("importPayload", mapper.readTree(json));
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(wrap);
        }
        PatternDocument doc = patternStore.get(patternId)
                .orElseThrow(() -> new IllegalArgumentException("Pattern 不存在: " + patternId));
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(doc);
    }

    private static String stringArg(Map<String, Object> args, String key, String defaultValue) {
        if (args == null || !args.containsKey(key) || args.get(key) == null) {
            return defaultValue;
        }
        return String.valueOf(args.get(key));
    }

    private static int intArg(Map<String, Object> args, String key, Integer defaultValue) {
        if (args == null || !args.containsKey(key) || args.get(key) == null) {
            return defaultValue != null ? defaultValue : 0;
        }
        if (args.get(key) instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(args.get(key)));
        } catch (NumberFormatException e) {
            return defaultValue != null ? defaultValue : 0;
        }
    }

    private static boolean booleanArg(Map<String, Object> args, String key, boolean defaultValue) {
        if (args == null || !args.containsKey(key) || args.get(key) == null) {
            return defaultValue;
        }
        Object v = args.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(v));
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringListArg(Map<String, Object> args, String key) {
        if (args == null || !args.containsKey(key)) {
            return List.of();
        }
        Object v = args.get(key);
        if (v instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
