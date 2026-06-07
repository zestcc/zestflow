package com.zestflow.mcp.config;

import java.nio.file.Path;

/**
 * MCP Server 运行时配置（CLI / 环境变量）。
 */
public record McpRuntimeConfig(
        Path projectRoot,
        String appCode,
        String adminBaseUrl,
        String bearerToken,
        String executorUrl,
        String executorAccessToken,
        Path auditLogPath,
        boolean auditEnabled,
        boolean exportTaskPackage,
        Path exportOutputPath
) {
    public boolean isServerMode() {
        return !exportTaskPackage;
    }
}
