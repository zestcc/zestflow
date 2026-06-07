package com.zestflow.mcp.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpRuntimeConfigParserTest {

    @Test
    void normalizeAdminBaseUrl_appendsApiZestflow() {
        assertEquals("https://admin.example.com/api/zestflow",
                McpRuntimeConfigParser.normalizeAdminBaseUrl("https://admin.example.com"));
        assertEquals("https://admin.example.com/api/zestflow",
                McpRuntimeConfigParser.normalizeAdminBaseUrl("https://admin.example.com/api/zestflow/"));
    }

    @Test
    void parse_requiresProject() {
        try {
            McpRuntimeConfigParser.parse(new String[]{"--app-code", "demo"});
            throw new AssertionError("expected exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("project"));
        }
    }

    @Test
    void parse_resolvesProjectDirectory() {
        Path cwd = Path.of(".").toAbsolutePath().normalize();
        McpRuntimeConfig config = McpRuntimeConfigParser.parse(new String[]{
                "--project", cwd.toString(),
                "--app-code", "demo-app"
        });
        assertEquals(cwd, config.projectRoot());
        assertEquals("demo-app", config.appCode());
        assertEquals("http://127.0.0.1:20550", config.executorUrl());
        assertNull(config.adminBaseUrl());
    }
}