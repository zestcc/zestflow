package com.zestflow.devinit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectMetadataResolverTest {

    @TempDir
    Path projectRoot;

    @Test
    void resolve_readsSpringApplicationNameAndExecutorPort() throws Exception {
        Files.createDirectories(projectRoot.resolve("src/main/resources"));
        Files.write(projectRoot.resolve("src/main/resources/application.yml"),
                "spring:\n  application:\n    name: ai-bookstore\nzestflow:\n  executor:\n    port: 21550\n".getBytes(StandardCharsets.UTF_8));

        ProjectMetadata metadata = ProjectMetadataResolver.resolve(projectRoot);

        assertEquals("ai-bookstore", metadata.appCode());
        assertEquals("http://127.0.0.1:21550", metadata.executorUrl());
    }

    @Test
    void readNestedValue_readsSpringApplicationName() {
        String yaml = "spring:\n  application:\n    name: ai-bookstore\n";
        assertEquals("ai-bookstore", ProjectMetadataResolver.readNestedValue(yaml, "spring", "application", "name"));
    }
}
