package com.zestflow.mcp.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.mcp.client.HttpApiClient;
import com.zestflow.mcp.config.McpRuntimeConfig;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 交付相关 MCP Tool 门面。
 */
public class DeliveryToolService {

    private final McpRuntimeConfig config;
    private final HttpApiClient apiClient;
    private final ObjectMapper mapper;
    private final DeliveryValidator validator;
    private final ChainComposeService composeService;
    private final SmokeSuiteGenerator smokeGenerator;
    private final AcceptanceSuiteRunner acceptanceRunner;

    public DeliveryToolService(McpRuntimeConfig config, HttpApiClient apiClient) {
        this.config = config;
        this.apiClient = apiClient;
        this.mapper = new ObjectMapper();
        this.validator = new DeliveryValidator();
        this.composeService = new ChainComposeService();
        this.smokeGenerator = new SmokeSuiteGenerator();
        this.acceptanceRunner = new AcceptanceSuiteRunner();
    }

    public String validateDelivery(Map<String, Object> args) throws Exception {
        boolean strict = booleanArg(args, "strictMode", true);
        DeliveryReport report = validator.validate(config.projectRoot(), config.appCode(), strict);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("passed", report.passed());
        out.put("score", report.score());
        out.put("usableScore", report.score());
        out.put("chainKeyCount", report.chainKeyCount());
        out.put("blocking", report.blocking());
        out.put("warnings", report.warnings());
        out.put("next_actions", report.nextActions());
        out.put("strictMode", strict);
        out.put("rule", "passed=true 且 usableScore≥0.95 方可向用户声明功能完成");
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(out);
    }

    public String composeChain(Map<String, Object> args) throws Exception {
        String patternId = stringArg(args, "patternId", stringArg(args, "pattern", "generic-crud"));
        String chainCode = stringArg(args, "chainCode", null);
        String chainName = stringArg(args, "chainName", stringArg(args, "feature", "业务链"));
        @SuppressWarnings("unchecked")
        Map<String, String> bindings = args != null && args.get("componentBindings") instanceof Map<?, ?> m
                ? (Map<String, String>) m
                : Map.of();

        ChainComposeService.ComposeResult result = composeService.compose(
                patternId, chainCode, chainName, bindings);

        String chainJson = (String) result.payload().get("chainDefinitionJson");
        Map<String, Object> validation = Map.of("skipped", true, "reason", "请调用 validate_chain 校验");
        if (chainJson != null && !chainJson.isBlank()) {
            try {
                String raw = apiClient.validateChain(config.appCode(), chainJson);
                validation = apiClient.parseValidationResponse(raw);
            } catch (Exception ex) {
                validation = Map.of("valid", false, "errors", java.util.List.of(ex.getMessage()));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>(result.payload());
        out.put("validateChain", validation);
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(out);
    }

    public String genSmokeSuite(Map<String, Object> args) throws Exception {
        return smokeGenerator.generate(config.projectRoot());
    }

    public String runAcceptanceSuite(Map<String, Object> args) throws Exception {
        String baseUrl = stringArg(args, "baseUrl", stringArg(args, "base-url", null));
        boolean dryRun = booleanArg(args, "dryRun", baseUrl == null || baseUrl.isBlank());
        return acceptanceRunner.run(config.projectRoot(), baseUrl, dryRun);
    }

    public static void runValidateDeliveryCli(Path projectRoot, String appCode, boolean strict) throws Exception {
        DeliveryReport report = new DeliveryValidator().validate(projectRoot, appCode, strict);
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("passed", report.passed());
        out.put("score", report.score());
        out.put("blocking", report.blocking());
        out.put("warnings", report.warnings());
        out.put("next_actions", report.nextActions());
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(out));
        if (!report.passed()) {
            System.exit(1);
        }
    }

    private static String stringArg(Map<String, Object> args, String key, String defaultValue) {
        if (args == null || !args.containsKey(key) || args.get(key) == null) {
            return defaultValue;
        }
        return String.valueOf(args.get(key));
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
}
