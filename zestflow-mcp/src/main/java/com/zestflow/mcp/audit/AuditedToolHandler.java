package com.zestflow.mcp.audit;

import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;
import java.util.function.Function;

/**
 * 包装 MCP Tool  handler，写入审计日志。
 */
public final class AuditedToolHandler {

    private AuditedToolHandler() {
    }

    public static Function<Map<String, Object>, McpSchema.CallToolResult> wrap(
            McpAuditLogger auditLogger,
            String toolName,
            Function<Map<String, Object>, McpSchema.CallToolResult> delegate) {
        return arguments -> {
            long start = System.currentTimeMillis();
            McpSchema.CallToolResult result = delegate.apply(arguments);
            long duration = System.currentTimeMillis() - start;
            boolean isError = result.isError() != null && result.isError();
            auditLogger.logToolCall(toolName, arguments, !isError, isError, duration,
                    isError ? firstContent(result) : null);
            return result;
        };
    }

    private static String firstContent(McpSchema.CallToolResult result) {
        if (result.content() == null || result.content().isEmpty()) {
            return null;
        }
        if (result.content().get(0) instanceof McpSchema.TextContent text) {
            return text.text();
        }
        return null;
    }
}
