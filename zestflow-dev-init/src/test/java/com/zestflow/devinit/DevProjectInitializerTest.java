package com.zestflow.devinit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevProjectInitializerTest {

    @TempDir
    Path projectRoot;

    @Test
    void initialize_createsProjectRulesAndCursorConfig() throws Exception {
        Files.createDirectories(projectRoot.resolve("src/main/resources"));
        writeUtf8(projectRoot.resolve("src/main/resources/application.yml"),
                "spring:\n  application:\n    name: bookstore-app\nzestflow:\n  executor:\n    port: 30550\n");
        writeUtf8(projectRoot.resolve("pom.xml"), "<project><artifactId>bookstore-app</artifactId></project>");

        DevInitOptions options = new DevInitOptions(
                "bookstore-app",
                "http://127.0.0.1:30550",
                ComponentizationMode.FULL,
                "component",
                HttpExposureMode.MODE3,
                EnumSet.of(
                        DevInitOptions.IdeTarget.CURSOR,
                        DevInitOptions.IdeTarget.VSCODE,
                        DevInitOptions.IdeTarget.CLAUDE),
                false,
                false,
                false);
        DevInitResult result = DevProjectInitializer.initialize(projectRoot, options);

        assertTrue(result.created().contains(".zestflow/rules/architecture.md"));
        assertTrue(result.created().contains(".cursor/mcp.json"));
        assertTrue(result.created().contains(".cursor/rules/zestflow-architecture.md"));

        String architectureMd = readUtf8(projectRoot.resolve(".zestflow/rules/architecture.md"));
        assertTrue(architectureMd.contains("bookstore-app"));
        assertTrue(architectureMd.contains("{模块根包}.component"));
        assertTrue(architectureMd.contains("full"));
        assertTrue(architectureMd.contains("@ZestComponent"));
        assertTrue(architectureMd.contains("@ZestChain"));
        assertTrue(architectureMd.contains("Repo"));
        assertTrue(architectureMd.contains("HTTP 模式"));
        assertTrue(architectureMd.contains("配置安全"));
        assertTrue(architectureMd.contains("H2"));
        assertTrue(!architectureMd.contains("zestory"));
        assertTrue(!architectureMd.contains("AuthService"));

        String mcpJson = readUtf8(projectRoot.resolve(".cursor/mcp.json"));
        assertTrue(mcpJson.contains("\"command\": \"java\""));
        assertTrue(mcpJson.contains("bookstore-app"));
    }

    @Test
    void initialize_hybridMode_mentionsService() throws Exception {
        Files.createDirectories(projectRoot.resolve("src/main/resources"));
        writeUtf8(projectRoot.resolve("src/main/resources/application.yml"),
                "spring:\n  application:\n    name: demo\n");

        DevInitOptions options = new DevInitOptions(
                "demo",
                "http://127.0.0.1:20550",
                ComponentizationMode.HYBRID,
                "component",
                HttpExposureMode.MODE1,
                EnumSet.of(DevInitOptions.IdeTarget.CURSOR),
                true,
                false,
                false);
        DevProjectInitializer.initialize(projectRoot, options);

        String architectureMd = readUtf8(projectRoot.resolve(".zestflow/rules/architecture.md"));
        assertTrue(architectureMd.contains("hybrid"));
        assertTrue(architectureMd.contains("Service` + `Mapper"));
    }

    @Test
    void substitute_replacesPlaceholders() {
        String out = DevProjectInitializer.substitute("app={{APP_CODE}} mode={{COMPONENTIZATION}}", Map.of(
                "APP_CODE", "my-app",
                "COMPONENTIZATION", "full"));
        assertEquals("app=my-app mode=full", out);
    }

    private static void writeUtf8(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    private static String readUtf8(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
