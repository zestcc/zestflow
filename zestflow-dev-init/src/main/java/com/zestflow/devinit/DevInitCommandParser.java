package com.zestflow.devinit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 解析 {@code --init-dev} CLI。
 */
public final class DevInitCommandParser {

    private DevInitCommandParser() {
    }

    public static boolean isInitDevCommand(String[] args) {
        for (String arg : args) {
            if ("--init-dev".equals(arg) || arg.startsWith("--init-dev=")) {
                return true;
            }
        }
        return false;
    }

    public static DevInitResult run(String[] args) throws IOException {
        Map<String, String> cli = parseArgs(args);
        if (!"true".equalsIgnoreCase(cli.getOrDefault("init-dev", "false"))) {
            throw new IllegalArgumentException("缺少 --init-dev");
        }

        String project = firstNonBlank(cli.get("project"), System.getenv("ZESTFLOW_PROJECT"));
        if (Strings.isBlank(project)) {
            throw new IllegalArgumentException("缺少 --project（或环境变量 ZESTFLOW_PROJECT）");
        }
        Path projectRoot = Paths.get(project).toAbsolutePath().normalize();
        if (!Files.isDirectory(projectRoot)) {
            throw new IllegalArgumentException("project 目录不存在: " + projectRoot);
        }

        ProjectMetadata detected = ProjectMetadataResolver.resolve(projectRoot);
        String appCode = firstNonBlank(cli.get("app-code"), System.getenv("ZESTFLOW_APP_CODE"), detected.appCode());
        String executorUrl = firstNonBlank(
                cli.get("executor-url"),
                System.getenv("ZESTFLOW_EXECUTOR_URL"),
                detected.executorUrl());
        ComponentizationMode componentization = ComponentizationMode.parse(cli.get("componentization"));
        String componentPackage = firstNonBlank(cli.get("component-package"), "component");
        HttpExposureMode httpMode = HttpExposureMode.parse(
                firstNonBlank(cli.get("http-mode"), cli.get("mode")));
        Set<DevInitOptions.IdeTarget> ides = DevInitOptions.IdeTarget.parseAll(cli.get("ide"));
        boolean force = "true".equalsIgnoreCase(cli.get("force"));
        boolean noGitignore = "true".equalsIgnoreCase(cli.get("no-gitignore"));
        boolean appendGitignore = !noGitignore;
        boolean bootstrapConfig = !"true".equalsIgnoreCase(cli.get("no-bootstrap-config"));

        DevInitOptions options = new DevInitOptions(
                appCode,
                executorUrl,
                componentization,
                componentPackage,
                httpMode,
                ides,
                force,
                appendGitignore,
                bootstrapConfig);
        return DevProjectInitializer.initialize(projectRoot, options);
    }

    public static void printResult(DevInitResult result) {
        System.out.println("# ZestFlow Dev Copilot 项目初始化");
        System.out.println();
        System.out.println("## 变量");
        for (Map.Entry<String, String> entry : result.variables().entrySet()) {
            String key = entry.getKey();
            if ("ARCHITECTURE_BODY".equals(key) || "COMPONENTIZATION_SECTION".equals(key)) {
                continue;
            }
            System.out.println("- " + key + ": " + entry.getValue());
        }
        System.out.println();
        if (!result.created().isEmpty()) {
            System.out.println("## 已创建/更新");
            for (String path : result.created()) {
                System.out.println("- " + path);
            }
            System.out.println();
        }
        if (!result.skipped().isEmpty()) {
            System.out.println("## 已跳过（文件已存在，使用 --force 覆盖）");
            for (String path : result.skipped()) {
                System.out.println("- " + path);
            }
            System.out.println();
        }
        if (result.warnings() != null && !result.warnings().isEmpty()) {
            System.out.println("## 警告（请处理后再依赖 MCP / 元件化）");
            for (String warning : result.warnings()) {
                System.out.println("- " + warning.replace("\n", "\n  "));
                System.out.println();
            }
        }
        System.out.println("Next:");
        System.out.println("1. 安装平台 JAR（一次）并启动本地 Executor");
        System.out.println("2. 刷新 MCP（见 .zestflow/mcp/README.md）");
        System.out.println("3. IDE 规则已同源生成：.zestflow/rules/architecture.md（规范源）");
        System.out.println("   - Cursor: .cursor/mcp.json + .cursor/rules/zestflow-architecture.md");
        System.out.println("   - VS Code / Cline: .vscode/mcp.json + .github/copilot-instructions.md");
        System.out.println("   - Claude Desktop: .zestflow/mcp/claude-desktop.config.json.example → 用户目录");
        System.out.println("   - Claude Code: .mcp.json（项目根，可提交 Git）");
        System.out.println("   - Windsurf: .zestflow/mcp/windsurf.mcp_config.json.example → 全局配置");
        System.out.println();
        System.out.println("init-dev 可用 Java 8+；MCP Server（zestflow-mcp.jar）运行需 Java 17+。");
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<String, String>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                continue;
            }
            String key = arg.substring(2);
            if (key.contains("=")) {
                int eq = key.indexOf('=');
                map.put(key.substring(0, eq), key.substring(eq + 1));
            } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                map.put(key, args[++i]);
            } else {
                map.put(key, "true");
            }
        }
        return map;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (Strings.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
