package com.zestflow.devinit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZestFlowConfigInspectorTest {

    @TempDir
    Path projectRoot;

    @Test
    void inspect_detectsMissingZestflowAndDatasource() throws Exception {
        Path module = projectRoot.resolve("demo-app");
        Files.createDirectories(module.resolve("src/main/resources"));
        Files.write(module.resolve("pom.xml"), "<project></project>".getBytes(StandardCharsets.UTF_8));
        Files.write(module.resolve("src/main/resources/application.yml"),
                "spring:\n  application:\n    name: x\n".getBytes(StandardCharsets.UTF_8));

        ZestFlowConfigInspector.ModuleConfigGaps gaps =
                ZestFlowConfigInspector.inspect(projectRoot, module);

        assertTrue(gaps.missingZestflowConfig);
        assertTrue(gaps.missingDatasourceConfig);
        assertTrue(gaps.missingStarterDependency);
        assertFalse(gaps.missingApplicationYml);
    }

    @Test
    void inspect_completeWhenAllPresent() throws Exception {
        Path module = projectRoot.resolve("demo-app");
        Files.createDirectories(module.resolve("src/main/resources"));
        Files.write(module.resolve("pom.xml"),
                "<dependency><artifactId>zestflow-starter</artifactId>".getBytes(StandardCharsets.UTF_8));
        Files.write(module.resolve("src/main/resources/application.yml"),
                "zestflow:\n  executor:\n    port: 20550\nspring:\n  datasource:\n    url: jdbc:mysql://x\n"
                        .getBytes(StandardCharsets.UTF_8));

        ZestFlowConfigInspector.ModuleConfigGaps gaps =
                ZestFlowConfigInspector.inspect(projectRoot, module);

        assertFalse(gaps.hasAnyGap());
    }
}
