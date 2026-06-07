package com.zestflow.mcp.dev;

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
        if (project == null || project.isBlank()) {
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
        String basePackage = firstNonBlank(cli.get("base-package"), detected.basePackage());
        Set<DevInitOptions.IdeTarget> ides = DevInitOptions.IdeTarget.parseAll(cli.get("ide"));
        boolean force = "true".equalsIgnoreCase(cli.get("force"));
        boolean noGitignore = "true".equalsIgnoreCase(cli.get("no-gitignore"));
        boolean appendGitignore = !noGitignore;

        DevInitOptions options = new DevInitOptions(appCode, executorUrl, basePackage, ides, force, appendGitignore);
        return DevProjectInitializer.initialize(projectRoot, options);
    }

    public static void printResult(DevInitResult result) {
        System.out.println("# ZestFlow Dev Copilot 项目初始化");
        System.out.println();
        System.out.println("## 变量");
        result.variables().forEach((key, value) -> System.out.println("- " + key + ": " + value));
        System.out.println();
        if (!result.created().isEmpty()) {
            System.out.println("## 已创建/更新");
            result.created().forEach(path -> System.out.println("- " + path));
            System.out.println();
        }
        if (!result.skipped().isEmpty()) {
            System.out.println("## 已跳过（文件已存在，使用 --force 覆盖）");
            result.skipped().forEach(path -> System.out.println("- " + path));
            System.out.println();
        }
        System.out.println("Next: 安装平台 MCP JAR（一次）并启动本地 Executor，然后在 IDE 中刷新 MCP。");
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
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
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
