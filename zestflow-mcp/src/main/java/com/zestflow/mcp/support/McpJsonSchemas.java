package com.zestflow.mcp.support;

import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;

/**
 * MCP Tool JSON Schema 辅助。
 */
public final class McpJsonSchemas {

    private McpJsonSchemas() {
    }

    public static McpSchema.JsonSchema objectSchema(Map<String, Object> properties, List<String> required) {
        return new McpSchema.JsonSchema(
                "object",
                properties,
                required,
                Boolean.FALSE,
                null,
                null);
    }

    public static Map<String, Object> stringProperty(String description) {
        return Map.of(
                "type", "string",
                "description", description);
    }
}
