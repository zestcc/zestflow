package com.zestflow.executor.ai;

import com.zestflow.executor.registry.ExecutorProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        Map<String, Object> status = service.ragStatus();
        assertTrue(((Number) status.get("patternCount")).intValue() >= 1);
    }

    @Test
    void recordLearningEvent_deduplicatesIdenticalPayload() throws Exception {
        ExecutorProperties props = new ExecutorProperties();
        props.setDataDir(tempDir.toString());
        ExecutorChainAiService service = new ExecutorChainAiService(props);

        Map<String, Object> body = baseEventBody();
        service.recordLearningEvent(body);
        Map<String, Object> second = service.recordLearningEvent(body);

        assertTrue((Boolean) second.get("deduplicated"));
        Map<String, Object> status = service.ragStatus();
        assertEquals(1, ((Number) status.get("eventCount")).intValue());
    }

    @Test
    void suggestChain_returnsEmptyWhenNoPatterns() throws Exception {
        ExecutorProperties props = new ExecutorProperties();
        props.setDataDir(tempDir.toString());
        ExecutorChainAiService service = new ExecutorChainAiService(props);

        Map<String, Object> suggest = service.suggestChain("用户注册", "CHN_DRAFT", List.of());
        assertTrue(suggest.get("source").toString().startsWith("executor-"));
        assertTrue(suggest.get("proposedChainData") == null);
    }

    private static Map<String, Object> baseEventBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("intent", "COMPOSE_CHAIN");
        body.put("feature", "orderCreate");
        body.put("validatePassed", true);
        body.put("validateRounds", 1);
        body.put("adopted", true);
        body.put("chainData", "{\"nodes\":[{\"id\":\"n1\"}],\"edges\":[]}");
        return body;
    }
}
