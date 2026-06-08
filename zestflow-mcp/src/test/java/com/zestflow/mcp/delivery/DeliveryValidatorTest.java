package com.zestflow.mcp.delivery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeliveryValidatorTest {

    @TempDir
    Path projectRoot;

    @Test
    void passesWhenAcceptanceAndPatternsPresent() throws Exception {
        Files.createDirectories(projectRoot.resolve(".zestflow/patterns"));
        Files.writeString(projectRoot.resolve(".zestflow/patterns/sample.md"), "# sample");
        Files.createDirectories(projectRoot.resolve(".zestflow/acceptance"));
        Files.writeString(projectRoot.resolve(".zestflow/acceptance/journeys.yml"), """
                version: 1
                journeys:
                  - id: health
                    steps:
                      - method: GET
                        path: "/health"
                        expect:
                          status: 200
                """);
        Files.writeString(projectRoot.resolve(".zestflow/acceptance/last-run.json"), """
                {"total":1,"passed":1,"passRate":1.0}
                """);

        DeliveryReport report = new DeliveryValidator(DeliveryDod.relaxed())
                .validate(projectRoot, "demo-app", false);
        assertTrue(report.passed());
        assertTrue(report.score() > 0.5);
    }

    @Test
    void blocksWhenAcceptanceMissing() throws Exception {
        DeliveryReport report = new DeliveryValidator().validate(projectRoot, "demo-app", true);
        assertFalse(report.passed());
        assertTrue(report.blocking().stream().anyMatch(b -> b.contains("ACCEPTANCE_MISSING")));
    }

    @Test
    void detectsProductionChainTopology() {
        String json = """
                {
                  "code": "CHN_TEST",
                  "nodes": [
                    {"id":"_start","type":"START"},
                    {"id":"n1","type":"EXECUTOR","component":"a"},
                    {"id":"n2","type":"EXECUTOR","component":"b"},
                    {"id":"_end","type":"END"}
                  ],
                  "edges": [
                    {"source":"_start","target":"n1"},
                    {"source":"n1","target":"n2"},
                    {"source":"n2","target":"_end"}
                  ],
                  "config": {"lifecycle":"production"}
                }
                """;
        assertTrue(DeliveryValidator.isConnectedProductionChain(json));
    }
}
