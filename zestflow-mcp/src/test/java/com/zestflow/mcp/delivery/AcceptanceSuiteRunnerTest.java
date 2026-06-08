package com.zestflow.mcp.delivery;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcceptanceSuiteRunnerTest {

    @Test
    void parseStepsFromYaml() {
        String yaml = """
                journeys:
                  - id: sample
                    steps:
                      - method: GET
                        path: "/api/books"
                        expect:
                          status: 200
                      - method: PUT
                        path: "/api/author/x"
                        expect:
                          status: 403
                """;
        List<AcceptanceSuiteRunner.StepCase> cases = AcceptanceSuiteRunner.parseSteps(yaml);
        assertEquals(2, cases.size());
        assertEquals("GET", cases.get(0).method());
        assertEquals(200, cases.get(0).expectedStatus());
        assertEquals(403, cases.get(1).expectedStatus());
    }

    @Test
    void parseStepsReturnsEmptyWhenNoSteps() {
        assertTrue(AcceptanceSuiteRunner.parseSteps("version: 1").isEmpty());
    }
}
