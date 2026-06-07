package com.zestflow.mcp.learning;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ChainPlanServiceTest {

    @Test
    void planRegister_detectsReuseAndGaps() {
        PatternSearcher searcher = new PatternSearcher(new PlatformPatternCatalog(),
                new PatternStore(Path.of(System.getProperty("java.io.tmpdir"))));
        ChainPlanService service = new ChainPlanService(searcher);
        ChainPlan plan = service.plan("帮我开发注册链路", "demo-app",
                Set.of("sendNotify", "validateUser"));
        assertEquals("userRegister", plan.feature());
        assertFalse(plan.steps().isEmpty());
        assertTrue(plan.gaps().stream().anyMatch(g -> "createUser".equals(g.componentId())));
        assertTrue(plan.steps().stream().anyMatch(s -> "sendNotify".equals(s.componentId())
                && "reuse".equals(s.reuseStatus())));
    }
}
