package com.zestflow.devinit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevProjectHealthCheckTest {

    @TempDir
    Path projectRoot;

    @Test
    void warnings_whenStarterAndExecutorPortMissing() throws Exception {
        Files.write(projectRoot.resolve("pom.xml"), "<project><artifactId>demo</artifactId></project>".getBytes(StandardCharsets.UTF_8));
        Path resources = projectRoot.resolve("demo-app/src/main/resources");
        Files.createDirectories(resources);
        Files.write(resources.resolve("application.yml"), "spring:\n  application:\n    name: demo-app\n".getBytes(StandardCharsets.UTF_8));

        List<String> warnings = DevProjectHealthCheck.warnings(projectRoot);

        assertFalse(warnings.isEmpty());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("zestflow-starter")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("executor.port")));
    }
}
