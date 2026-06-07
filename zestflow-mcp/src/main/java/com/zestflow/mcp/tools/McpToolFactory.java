package com.zestflow.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.mcp.audit.AuditedToolHandler;
import com.zestflow.mcp.audit.McpAuditLogger;
import com.zestflow.mcp.client.HttpApiClient;
import com.zestflow.mcp.config.McpRuntimeConfig;
import com.zestflow.mcp.learning.LearningToolService;
import com.zestflow.mcp.export.TaskPackageExporter;
import com.zestflow.mcp.io.ResourceLoader;
import com.zestflow.mcp.scaffold.ComponentScaffoldGenerator;
import com.zestflow.mcp.search.ProjectSourceSearcher;
import com.zestflow.mcp.support.McpJsonSchemas;
import com.zestflow.mcp.support.McpToolResults;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 注册全部 MCP Tools（含审计包装）。
 */
public class McpToolFactory {

    private static final Logger log = LoggerFactory.getLogger(McpToolFactory.class);

    private final McpRuntimeConfig config;
    private final ObjectMapper objectMapper;
    private final HttpApiClient apiClient;
    private final McpAuditLogger auditLogger;
    private final ProjectSourceSearcher sourceSearcher = new ProjectSourceSearcher();
    private final ComponentScaffoldGenerator scaffoldGenerator = new ComponentScaffoldGenerator();
    private final LearningToolService learningTools;

    public McpToolFactory(McpRuntimeConfig config, ObjectMapper objectMapper,
                          HttpApiClient apiClient, McpAuditLogger auditLogger) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.apiClient = apiClient;
        this.auditLogger = auditLogger;
        this.learningTools = new LearningToolService(config, apiClient);
    }

    public List<McpServerFeatures.SyncToolSpecification> buildAll() {
        List<McpServerFeatures.SyncToolSpecification> tools = new ArrayList<>();
        tools.addAll(List.of(
                tool("list_components", listComponentsProperties(),
                        """
                                列出已注册 ZestFlow 元件白名单（componentId、类型、分组）。
                                生成新元件或引用链节点前必须先调用本工具，禁止编造 componentId。
                                """,
                        List.of(), this::handleListComponents),
                tool("read_project_file", readFileProperties(),
                        "读取本地 Executor/业务工程内的源码或配置文件（只读，限制在 --project 目录内）。",
                        List.of("relativePath"), this::handleReadProjectFile),
                tool("validate_chain", validateChainProperties(),
                        """
                                调用 Executor ChainValidator 校验链定义。
                                生成或修改链 JSON 后必须调用；valid=false 时不得视为可发布。
                                """,
                        List.of("chainDefinitionJson"), this::handleValidateChain),
                tool("search_sources", searchProperties(),
                        """
                                在 --project 内按关键词搜索源码（默认 **/*.java）。
                                不确定参考类路径时，先 search 再 read_project_file。
                                """,
                        List.of("keyword"), this::handleSearchSources),
                tool("scaffold_component", scaffoldProperties(),
                        """
                                生成 @ZestComponent Java 脚手架（仅返回文本与建议路径，不写盘）。
                                生成前必须先 list_components；落盘由 IDE Apply 完成。
                                """,
                        List.of("componentId"), this::handleScaffoldComponent),
                tool("export_task_package", exportTaskProperties(),
                        """
                                导出 Cursor 任务包 Markdown（规范摘要 + 元件白名单 + 项目规则）。
                                适用于未配置 MCP 时手动 @ 进聊天；与 MCP Resources 同源。
                                """,
                        List.of(), this::handleExportTaskPackage)
        ));
        tools.addAll(List.of(
                tool("plan_chain", planChainProperties(),
                        """
                                【意图：开发链路】Chain-first 业务链规划：拆解步骤、元件类型混用、白名单对比(gap)、检索平台/项目 Pattern。
                                关键字：开发链路、注册链、规划链。完成后按 workflowNext 继续 scaffold/validate。
                                """,
                        List.of("description"), this::handlePlanChain),
                tool("record_learning_event", learningEventProperties(),
                        """
                                【意图：反馈沉淀 P1】记录一次工作流结果（validate/采纳/Playground/修正）。
                                仅高置信事件可被 distill_patterns 晋升（目标准确率≥97%）。
                                """,
                        List.of("intent", "feature"), this::handleRecordLearningEvent),
                tool("search_patterns", searchPatternsProperties(),
                        """
                                【意图：检索经验 P2】搜索平台(L1)+项目(L2) Pattern，plan_chain 前/后均可调用。
                                """,
                        List.of("query"), this::handleSearchPatterns),
                tool("distill_patterns", distillPatternsProperties(),
                        """
                                【意图：蒸馏 P2】将 events.jsonl 中高置信事件蒸馏为 .zestflow/patterns/*.md。
                                """,
                        List.of(), this::handleDistillPatterns),
                tool("gen_playground_scene", genSceneProperties(),
                        """
                                【意图：生成场景】按 HTTP Mode1/2/3 生成 Playground 场景草稿与示例 body。
                                """,
                        List.of("feature", "chainCode", "httpMode"), this::handleGenPlaygroundScene),
                tool("share_pattern", sharePatternProperties(),
                        """
                                【意图：共享 P3】导出 Pattern 为 Admin RAG import 载荷，供团队继承。
                                """,
                        List.of("patternId"), this::handleSharePattern)
        ));
        return tools;
    }

    private McpServerFeatures.SyncToolSpecification tool(
            String name,
            Map<String, Object> properties,
            String description,
            List<String> required,
            java.util.function.Function<Map<String, Object>, McpSchema.CallToolResult> handler) {
        var audited = AuditedToolHandler.wrap(auditLogger, name, handler);
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(name, description, McpJsonSchemas.objectSchema(properties, required)),
                (exchange, arguments) -> audited.apply(arguments));
    }

    private McpSchema.CallToolResult handleListComponents(Map<String, Object> arguments) {
        try {
            String appCode = stringArg(arguments, "appCode", config.appCode());
            return McpToolResults.text(apiClient.listComponents(appCode));
        } catch (Exception e) {
            log.warn("list_components failed", e);
            return McpToolResults.error("list_components 失败: " + e.getMessage());
        }
    }

    private McpSchema.CallToolResult handleReadProjectFile(Map<String, Object> arguments) {
        try {
            String relativePath = stringArg(arguments, "relativePath", null);
            if (relativePath == null || relativePath.isBlank()) {
                return McpToolResults.error("relativePath 不能为空");
            }
            return McpToolResults.text(ResourceLoader.readProjectFile(config.projectRoot(), relativePath));
        } catch (Exception e) {
            log.warn("read_project_file failed", e);
            return McpToolResults.error("read_project_file 失败: " + e.getMessage());
        }
    }

    private McpSchema.CallToolResult handleValidateChain(Map<String, Object> arguments) {
        try {
            String appCode = stringArg(arguments, "appCode", config.appCode());
            String chainJson = stringArg(arguments, "chainDefinitionJson", null);
            if (chainJson == null || chainJson.isBlank()) {
                return McpToolResults.error("chainDefinitionJson 不能为空");
            }
            String raw = apiClient.validateChain(appCode, chainJson);
            Map<String, Object> parsed = apiClient.parseValidationResponse(raw);
            return McpToolResults.text(objectMapper.writeValueAsString(parsed));
        } catch (Exception e) {
            log.warn("validate_chain failed", e);
            return McpToolResults.error("validate_chain 失败: " + e.getMessage());
        }
    }

    private McpSchema.CallToolResult handleSearchSources(Map<String, Object> arguments) {
        try {
            String keyword = stringArg(arguments, "keyword", null);
            String glob = stringArg(arguments, "glob", "**/*.java");
            int maxResults = intArg(arguments, "maxResults", 20);
            String json = sourceSearcher.search(config.projectRoot(), keyword, glob, maxResults);
            return McpToolResults.text(json);
        } catch (Exception e) {
            log.warn("search_sources failed", e);
            return McpToolResults.error("search_sources 失败: " + e.getMessage());
        }
    }

    private McpSchema.CallToolResult handleScaffoldComponent(Map<String, Object> arguments) {
        try {
            String componentId = stringArg(arguments, "componentId", null);
            String json = scaffoldGenerator.scaffold(
                    config.projectRoot(),
                    componentId,
                    stringArg(arguments, "componentType", "EXECUTOR"),
                    stringArg(arguments, "groupName", "generated"),
                    stringArg(arguments, "description", null),
                    stringArg(arguments, "packageName", null));
            return McpToolResults.text(json);
        } catch (Exception e) {
            log.warn("scaffold_component failed", e);
            return McpToolResults.error("scaffold_component 失败: " + e.getMessage());
        }
    }

    private McpSchema.CallToolResult handleExportTaskPackage(Map<String, Object> arguments) {
        try {
            String appCode = stringArg(arguments, "appCode", config.appCode());
            boolean includeSamples = booleanArg(arguments, "includeSampleSearch", true);
            String md = new TaskPackageExporter(config, apiClient).exportMarkdown(appCode, includeSamples);
            return McpToolResults.text(md);
        } catch (Exception e) {
            log.warn("export_task_package failed", e);
            return McpToolResults.error("export_task_package 失败: " + e.getMessage());
        }
    }

    private McpSchema.CallToolResult handlePlanChain(Map<String, Object> arguments) {
        return learningCall("plan_chain", () -> learningTools.planChain(arguments));
    }

    private McpSchema.CallToolResult handleRecordLearningEvent(Map<String, Object> arguments) {
        return learningCall("record_learning_event", () -> learningTools.recordLearningEvent(arguments));
    }

    private McpSchema.CallToolResult handleSearchPatterns(Map<String, Object> arguments) {
        return learningCall("search_patterns", () -> learningTools.searchPatterns(arguments));
    }

    private McpSchema.CallToolResult handleDistillPatterns(Map<String, Object> arguments) {
        return learningCall("distill_patterns", () -> learningTools.distillPatterns(arguments));
    }

    private McpSchema.CallToolResult handleGenPlaygroundScene(Map<String, Object> arguments) {
        return learningCall("gen_playground_scene", () -> learningTools.genPlaygroundScene(arguments));
    }

    private McpSchema.CallToolResult handleSharePattern(Map<String, Object> arguments) {
        return learningCall("share_pattern", () -> learningTools.sharePattern(arguments));
    }

    private McpSchema.CallToolResult learningCall(String name, LearningCallable callable) {
        try {
            return McpToolResults.text(callable.call());
        } catch (Exception e) {
            log.warn("{} failed", name, e);
            return McpToolResults.error(name + " 失败: " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface LearningCallable {
        String call() throws Exception;
    }

    private static Map<String, Object> planChainProperties() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("description", McpJsonSchemas.stringProperty("业务描述，如：帮我开发注册链路"));
        p.put("userMessage", McpJsonSchemas.stringProperty("同 description"));
        p.put("appCode", McpJsonSchemas.stringProperty("应用编码"));
        return p;
    }

    private static Map<String, Object> learningEventProperties() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("intent", McpJsonSchemas.stringProperty("PLAN_CHAIN|SCAFFOLD_COMPONENT|COMPOSE_CHAIN|VERIFY|..."));
        p.put("feature", McpJsonSchemas.stringProperty("如 userRegister"));
        p.put("chainCode", McpJsonSchemas.stringProperty("链编码"));
        p.put("httpMode", Map.of("type", "integer", "description", "1|2|3"));
        p.put("validatePassed", Map.of("type", "boolean"));
        p.put("validateRounds", Map.of("type", "integer"));
        p.put("adopted", Map.of("type", "boolean"));
        p.put("playgroundSuccess", Map.of("type", "boolean"));
        p.put("userCorrection", McpJsonSchemas.stringProperty("用户修正说明"));
        p.put("reusedComponents", Map.of("type", "array", "items", Map.of("type", "string")));
        p.put("createdComponents", Map.of("type", "array", "items", Map.of("type", "string")));
        return p;
    }

    private static Map<String, Object> searchPatternsProperties() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("query", McpJsonSchemas.stringProperty("检索词"));
        p.put("limit", Map.of("type", "integer", "description", "默认 5"));
        return p;
    }

    private static Map<String, Object> distillPatternsProperties() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("feature", McpJsonSchemas.stringProperty("可选，过滤 feature"));
        return p;
    }

    private static Map<String, Object> genSceneProperties() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("feature", McpJsonSchemas.stringProperty("业务特征名"));
        p.put("chainCode", McpJsonSchemas.stringProperty("链编码"));
        p.put("httpMode", Map.of("type", "integer", "description", "1=/execute 2=链路由 3=Controller"));
        return p;
    }

    private static Map<String, Object> sharePatternProperties() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("patternId", McpJsonSchemas.stringProperty("项目 patterns 下的 id"));
        p.put("mode", McpJsonSchemas.stringProperty("team_export|read"));
        return p;
    }

    private static Map<String, Object> listComponentsProperties() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("appCode", McpJsonSchemas.stringProperty("应用编码；默认 --app-code"));
        return p;
    }

    private static Map<String, Object> readFileProperties() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("relativePath", McpJsonSchemas.stringProperty("相对 --project 的路径"));
        return p;
    }

    private static Map<String, Object> validateChainProperties() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("appCode", McpJsonSchemas.stringProperty("应用编码；默认 --app-code"));
        p.put("chainDefinitionJson", McpJsonSchemas.stringProperty("ChainDefinition JSON"));
        return p;
    }

    private static Map<String, Object> searchProperties() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("keyword", McpJsonSchemas.stringProperty("搜索关键词（匹配文件行内容）"));
        p.put("glob", McpJsonSchemas.stringProperty("路径 glob，默认 **/*.java"));
        p.put("maxResults", Map.of("type", "integer", "description", "最大命中数，默认 20，上限 50"));
        return p;
    }

    private static Map<String, Object> scaffoldProperties() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("componentId", McpJsonSchemas.stringProperty("全局唯一 componentId"));
        p.put("componentType", McpJsonSchemas.stringProperty("EXECUTOR|PREDICATE|... 默认 EXECUTOR"));
        p.put("groupName", McpJsonSchemas.stringProperty("@ZestComponent 分组名"));
        p.put("description", McpJsonSchemas.stringProperty("业务描述"));
        p.put("packageName", McpJsonSchemas.stringProperty("Java 包名；可省略，从项目规则推断"));
        return p;
    }

    private static Map<String, Object> exportTaskProperties() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("appCode", McpJsonSchemas.stringProperty("应用编码；默认 --app-code"));
        p.put("includeSampleSearch", Map.of("type", "boolean", "description", "是否附带 @ZestComponent 源码抽样"));
        return p;
    }

    private static String stringArg(Map<String, Object> args, String key, String defaultValue) {
        if (args == null || !args.containsKey(key) || args.get(key) == null) {
            return defaultValue;
        }
        return String.valueOf(args.get(key));
    }

    private static int intArg(Map<String, Object> args, String key, int defaultValue) {
        if (args == null || !args.containsKey(key) || args.get(key) == null) {
            return defaultValue;
        }
        if (args.get(key) instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(args.get(key)));
        } catch (NumberFormatException e) {
            return defaultValue;
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
}
