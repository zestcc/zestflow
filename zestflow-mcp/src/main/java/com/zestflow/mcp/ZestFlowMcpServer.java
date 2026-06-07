package com.zestflow.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.mcp.audit.McpAuditLogger;
import com.zestflow.mcp.client.HttpApiClient;
import com.zestflow.mcp.config.McpRuntimeConfig;
import com.zestflow.mcp.export.TaskPackageExporter;
import com.zestflow.mcp.io.ResourceLoader;
import com.zestflow.mcp.tools.McpToolFactory;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 组装 MCP Server：Resources + Tools。
 */
public class ZestFlowMcpServer {

    private static final Logger log = LoggerFactory.getLogger(ZestFlowMcpServer.class);

    private static final List<RuleResource> RULE_RESOURCES = List.of(
            new RuleResource("zestflow://rules/component", "component-development.md", "ZestFlow 元件开发规范"),
            new RuleResource("zestflow://rules/chain", "chain-definition.md", "ZestFlow 链定义规范"),
            new RuleResource("zestflow://rules/anti-patterns", "anti-patterns.md", "ZestFlow 反模式与禁止项"),
            new RuleResource("zestflow://rules/aviator", "aviator-expressions.md", "Aviator 表达式约定"),
            new RuleResource("zestflow://schema/chain-definition", "schemas/chain-definition.schema.json", "链定义 JSON Schema"),
            new RuleResource("zestflow://examples/component/execute", "examples/SampleExecuteComponent.java", "标准 @ZestExecute 示例")
    );

    private final McpRuntimeConfig config;
    private final ObjectMapper objectMapper;
    private final HttpApiClient apiClient;
    private final McpAuditLogger auditLogger;
    private McpSyncServer server;

    public ZestFlowMcpServer(McpRuntimeConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.apiClient = new HttpApiClient(config);
        this.auditLogger = new McpAuditLogger(config.auditLogPath(), config.auditEnabled());
    }

    public void start() {
        McpToolFactory toolFactory = new McpToolFactory(config, objectMapper, apiClient, auditLogger);
        StdioServerTransportProvider transport = new StdioServerTransportProvider(objectMapper);
        this.server = McpServer.sync(transport)
                .serverInfo("zestflow-mcp", "0.2.0")
                .instructions(buildInstructions())
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .resources(false, true)
                        .tools(true)
                        .logging()
                        .build())
                .resources(buildResources())
                .tools(toolFactory.buildAll().toArray(McpServerFeatures.SyncToolSpecification[]::new))
                .build();
        log.info("zestflow-mcp ready (stdio), tools=12, audit={}", config.auditEnabled());
    }

    public static void exportTaskPackage(McpRuntimeConfig config) throws Exception {
        HttpApiClient client = new HttpApiClient(config);
        String markdown = new TaskPackageExporter(config, client).exportMarkdown(config.appCode(), true);
        if (config.exportOutputPath() != null) {
            java.nio.file.Files.writeString(config.exportOutputPath(), markdown);
            System.err.println("Task package written to " + config.exportOutputPath());
        } else {
            System.out.print(markdown);
        }
    }

    public void closeGracefully() {
        if (server != null) {
            server.closeGracefully();
        }
    }

    private String buildInstructions() {
        return """
                你是 ZestFlow 元件/链条开发助手。必须按 Chain-first 编排链思路：plan → scaffold → compose → validate → HTTP Mode → 场景 → 反馈沉淀。
                必须遵守 MCP Resources 中的官方规范与项目 rules/project。
                生成新元件前必须先 list_components；生成或修改链 JSON 后必须调用 validate_chain。
                用户说「开发链路/注册链」→ plan_chain；「生成元件」→ scaffold_component；「生成场景」→ gen_playground_scene。
                完成后 record_learning_event；积累足够高置信样本后 distill_patterns；团队共享 share_pattern。
                禁止编造 componentId、禁止自动 publish/reload。
                源码落盘由 Cursor/Claude Apply 完成；禁止 write_project_file。
                准确率目标≥97%%：validate 通过且（采纳或 Playground 成功）才 record。
                """;
    }

    private McpServerFeatures.SyncResourceSpecification[] buildResources() {
        McpServerFeatures.SyncResourceSpecification[] specs =
                new McpServerFeatures.SyncResourceSpecification[RULE_RESOURCES.size() + 1];

        for (int i = 0; i < RULE_RESOURCES.size(); i++) {
            RuleResource rule = RULE_RESOURCES.get(i);
            specs[i] = classpathResource(rule.uri(), rule.name(), rule.classpathPath());
        }

        specs[RULE_RESOURCES.size()] = new McpServerFeatures.SyncResourceSpecification(
                new McpSchema.Resource(
                        "zestflow://rules/project",
                        "project-rules",
                        "项目自定义规则（.zestflow/rules/project.md，追加于官方规范）",
                        "text/markdown",
                        null),
                (exchange, request) -> {
                    String official = summarizeOfficialRules();
                    String projectRules = ResourceLoader.readProjectRules(config.projectRoot());
                    String merged = """
                            # ZestFlow 项目规则（L1 官方摘要 + L2 项目追加）

                            ## L0 平台硬约束（不可覆盖）
                            - 不得编造未注册的 componentId
                            - 链定义必须通过 validate_chain
                            - 禁止自动 publish / reload / 写生产配置
                            - 源码由 IDE Apply 落盘，MCP 不写盘

                            ## L1 官方规范摘要
                            """ + official + """

                            ## L2 项目规则（.zestflow/rules/project.md）
                            """ + (projectRules.isBlank()
                            ? "（未配置，可在项目根创建 `.zestflow/rules/project.md`）"
                            : projectRules);
                    return new McpSchema.ReadResourceResult(List.of(
                            new McpSchema.TextResourceContents(
                                    "zestflow://rules/project",
                                    "text/markdown",
                                    merged)));
                });
        return specs;
    }

    private McpServerFeatures.SyncResourceSpecification classpathResource(
            String uri, String name, String classpathPath) {
        return new McpServerFeatures.SyncResourceSpecification(
                new McpSchema.Resource(uri, name, name, mimeTypeFor(classpathPath), null),
                (exchange, request) -> {
                    try {
                        String text = ResourceLoader.readClasspath("zestflow/" + classpathPath);
                        return new McpSchema.ReadResourceResult(List.of(
                                new McpSchema.TextResourceContents(uri, mimeTypeFor(classpathPath), text)));
                    } catch (Exception e) {
                        throw new IllegalStateException("读取规范失败: " + classpathPath, e);
                    }
                });
    }

    private String summarizeOfficialRules() {
        StringBuilder sb = new StringBuilder();
        for (RuleResource rule : RULE_RESOURCES) {
            if (rule.classpathPath().endsWith(".md")) {
                try {
                    String text = ResourceLoader.readClasspath("zestflow/" + rule.classpathPath());
                    sb.append("\n### ").append(rule.name()).append("\n");
                    sb.append(text.lines().limit(12).reduce((a, b) -> a + "\n" + b).orElse(""));
                    sb.append("\n\n> 完整内容请读取 Resource: ").append(rule.uri()).append("\n");
                } catch (Exception ignored) {
                    sb.append("\n- ").append(rule.uri()).append("\n");
                }
            }
        }
        return sb.toString();
    }

    private static String mimeTypeFor(String path) {
        if (path.endsWith(".json")) {
            return "application/json";
        }
        if (path.endsWith(".java")) {
            return "text/x-java-source";
        }
        return "text/markdown";
    }

    private record RuleResource(String uri, String classpathPath, String name) {
    }
}
