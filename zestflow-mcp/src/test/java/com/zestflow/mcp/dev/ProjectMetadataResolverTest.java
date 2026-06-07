package com.zestflow.mcp.dev;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectMetadataResolverTest {

    @TempDir
    Path projectRoot;

    @Test
    void resolve_readsSpringApplicationNameAndExecutorPort() throws Exception {
        Files.createDirectories(projectRoot.resolve("src/main/resources"));
        Files.writeString(projectRoot.resolve("src/main/resources/application.yml"), """
                spring:
                  application:
                    name: ai-bookstore
                zestflow:
                  executor:
                    port: 21550
                """);
        Files.writeString(projectRoot.resolve("pom.xml"), """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>ai-bookstore</artifactId>
                </project>
                """);

        ProjectMetadata metadata = ProjectMetadataResolver.resolve(projectRoot);

        assertEquals("ai-bookstore", metadata.appCode());
        assertEquals("http://127.0.0.1:21550", metadata.executorUrl());
        assertEquals("com.example.ai.bookstore", metadata.basePackage());
    }

    @Test
    void readNestedValue_readsSpringApplicationName() {
        String yaml = """
                spring:
                  application:
                    name: ai-bookstore
                zestflow:
                  executor:
                    port: 21550
                """;
        assertEquals("ai-bookstore", ProjectMetadataResolver.readNestedValue(yaml, "spring", "application", "name"));
        assertEquals("21550", ProjectMetadataResolver.readNestedValue(yaml, "zestflow", "executor", "port"));
    }

    @Test
    void resolveBasePackage_ignoresParentArtifactId() {
        String pom = """
                <project>
                  <parent>
                    <groupId>cn.zestflow.www</groupId>
                    <artifactId>zestflow</artifactId>
                  </parent>
                  <artifactId>zestflow-demo</artifactId>
                </project>
                """;
        assertEquals("cn.zestflow.www.zestflow.demo", ProjectMetadataResolver.resolveBasePackage(pom));
    }
}
