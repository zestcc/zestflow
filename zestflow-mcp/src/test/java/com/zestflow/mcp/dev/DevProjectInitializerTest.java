package com.zestflow.mcp.dev;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevProjectInitializerTest {

    @TempDir
    Path projectRoot;

    @Test
    void initialize_createsProjectRulesAndCursorConfig() throws Exception {
        Files.createDirectories(projectRoot.resolve("src/main/resources"));
        Files.writeString(projectRoot.resolve("src/main/resources/application.yml"), """
                spring:
                  application:
                    name: bookstore-app
                zestflow:
                  executor:
                    port: 30550
                """);

        DevInitOptions options = new DevInitOptions(
                "bookstore-app",
                "http://127.0.0.1:30550",
                "com.acme.bookstore",
                Set.of(DevInitOptions.IdeTarget.CURSOR),
                false,
                false);
        DevInitResult result = DevProjectInitializer.initialize(projectRoot, options);

        assertTrue(result.created().contains(".zestflow/rules/project.md"));
        assertTrue(result.created().contains(".cursor/mcp.json"));
        assertTrue(Files.isDirectory(projectRoot.resolve(".zestflow/learning")));

        String projectMd = Files.readString(projectRoot.resolve(".zestflow/rules/project.md"));
        assertTrue(projectMd.contains("bookstore-app"));
        assertTrue(projectMd.contains("com.acme.bookstore"));

        String mcpJson = Files.readString(projectRoot.resolve(".cursor/mcp.json"));
        assertTrue(mcpJson.contains("bookstore-app"));
        assertTrue(mcpJson.contains("http://127.0.0.1:30550"));
    }

    @Test
    void initialize_skipsExistingUnlessForce() throws Exception {
        Files.createDirectories(projectRoot.resolve(".zestflow/rules"));
        Files.writeString(projectRoot.resolve(".zestflow/rules/project.md"), "keep");

        DevInitOptions options = new DevInitOptions(
                "demo",
                "http://127.0.0.1:20550",
                "com.example.app",
                Set.of(DevInitOptions.IdeTarget.CURSOR),
                false,
                false);
        DevInitResult result = DevProjectInitializer.initialize(projectRoot, options);

        assertTrue(result.skipped().contains(".zestflow/rules/project.md"));
        assertEquals("keep", Files.readString(projectRoot.resolve(".zestflow/rules/project.md")));
    }

    @Test
    void substitute_replacesPlaceholders() {
        String out = DevProjectInitializer.substitute("app={{APP_CODE}} url={{EXECUTOR_URL}}", Map.of(
                "APP_CODE", "my-app",
                "EXECUTOR_URL", "http://127.0.0.1:20550"));
        assertEquals("app=my-app url=http://127.0.0.1:20550", out);
    }
}
