package com.zestflow.mcp.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 解析 CLI 参数与环境变量。
 * <p>
 * 环境变量：ZESTFLOW_PROJECT、ZESTFLOW_APP_CODE、ZESTFLOW_ADMIN_URL、ZESTFLOW_TOKEN、
 * ZESTFLOW_EXECUTOR_URL、ZESTFLOW_EXECUTOR_TOKEN。
 */
public final class McpRuntimeConfigParser {

    private McpRuntimeConfigParser() {
    }

    public static McpRuntimeConfig parse(String[] args) {
        Map<String, String> cli = parseArgs(args);

        String project = firstNonBlank(
                cli.get("project"),
                System.getenv("ZESTFLOW_PROJECT"));
        if (project == null || project.isBlank()) {
            throw new IllegalArgumentException("缺少 --project（或环境变量 ZESTFLOW_PROJECT）");
        }
        Path projectRoot = Paths.get(project).toAbsolutePath().normalize();
        if (!Files.isDirectory(projectRoot)) {
            throw new IllegalArgumentException("project 目录不存在: " + projectRoot);
        }

        String appCode = firstNonBlank(
                cli.get("app-code"),
                System.getenv("ZESTFLOW_APP_CODE"),
                "demo");

        String adminUrl = normalizeAdminBaseUrl(firstNonBlank(
                cli.get("admin-url"),
                System.getenv("ZESTFLOW_ADMIN_URL")));

        String token = firstNonBlank(
                cli.get("token"),
                System.getenv("ZESTFLOW_TOKEN"));

        String executorUrl = normalizeBaseUrl(firstNonBlank(
                cli.get("executor-url"),
                System.getenv("ZESTFLOW_EXECUTOR_URL"),
                "http://127.0.0.1:20550"));

        String executorToken = firstNonBlank(
                cli.get("executor-token"),
                System.getenv("ZESTFLOW_EXECUTOR_TOKEN"));

        boolean exportTaskPackage = "true".equalsIgnoreCase(cli.get("export-task-package"));
        boolean auditEnabled = !"true".equalsIgnoreCase(cli.get("no-audit-log"));
        Path auditLogPath = resolveAuditLogPath(projectRoot, cli.get("audit-log"));
        Path exportOutput = resolveExportOutput(cli.get("output"), cli.get("o"));

        return new McpRuntimeConfig(
                projectRoot,
                appCode,
                adminUrl,
                token,
                executorUrl,
                executorToken,
                auditLogPath,
                auditEnabled,
                exportTaskPackage,
                exportOutput);
    }

    /**
     * Admin 侧任务包导出（无本地 project）。
     */
    public static McpRuntimeConfig forRemoteExport(String appCode, String adminBaseUrl, String bearerToken) {
        return new McpRuntimeConfig(
                null,
                appCode,
                normalizeAdminBaseUrl(adminBaseUrl),
                bearerToken,
                null,
                null,
                null,
                false,
                false,
                null);
    }

    private static Path resolveAuditLogPath(Path projectRoot, String explicit) {
        if (explicit != null && !explicit.isBlank()) {
            return Paths.get(explicit).toAbsolutePath().normalize();
        }
        return projectRoot.resolve(".zestflow/mcp-audit.jsonl");
    }

    private static Path resolveExportOutput(String output, String shortOpt) {
        String path = firstNonBlank(output, shortOpt);
        if (path == null || path.isBlank()) {
            return null;
        }
        return Paths.get(path).toAbsolutePath().normalize();
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

    static String normalizeAdminBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim().replaceAll("/+$", "");
        if (trimmed.endsWith("/api/zestflow")) {
            return trimmed;
        }
        if (trimmed.endsWith("/api")) {
            return trimmed + "/zestflow";
        }
        return trimmed + "/api/zestflow";
    }

    static String normalizeBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        return url.trim().replaceAll("/+$", "");
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
