package com.zestflow.mcp.support;

import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;

/**
 * MCP Tool 结果辅助。
 */
public final class McpToolResults {

    private McpToolResults() {
    }

    public static McpSchema.CallToolResult text(String text) {
        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(text)))
                .isError(false)
                .build();
    }

    public static McpSchema.CallToolResult error(String message) {
        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(message)))
                .isError(true)
                .build();
    }
}
