package com.zestflow.executor.ai;

import com.zestflow.executor.registry.ExecutorProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutorChainAiServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void recordAndDistill_highQualityEvent() throws Exception {
        ExecutorProperties props = new ExecutorProperties();
        props.setDataDir(tempDir.toString());
        ExecutorChainAiService service = new ExecutorChainAiService(props);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("intent", "COMPOSE_CHAIN");
        body.put("feature", "userRegister");
        body.put("chainCode", "CHN_USER_REGISTER");
        body.put("validatePassed", true);
        body.put("validateRounds", 1);
        body.put("adopted", true);
        body.put("chainData", "{\"nodes\":[{\"id\":\"a\"}],\"edges\":[]}");

        Map<String, Object> result = service.recordLearningEvent(body);
        assertTrue((Boolean) result.get("promotionEligible"));
        assertTrue((Boolean) result.get("autoDistilled"));

        List<String> rag = service.searchRag("userRegister", 5);
        assertFalse(rag.isEmpty());
    }
}
