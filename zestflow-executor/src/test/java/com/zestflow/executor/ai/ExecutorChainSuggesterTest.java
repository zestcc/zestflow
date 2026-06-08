package com.zestflow.executor.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutorChainSuggesterTest {

    @Test
    void suggestFromPattern_extractsJsonBlock() {
        ExecutorAiProperties props = new ExecutorAiProperties();
        props.setLlmEnabled(false);
        ExecutorChainSuggester suggester = new ExecutorChainSuggester(props, null);

        String markdown = """
                # Pattern
                ```json
                {"nodes":[{"id":"a","type":"NORMAL"}],"edges":[]}
                ```
                """;
        List<ExecutorRagChunk> chunks = List.of(new ExecutorRagChunk("p1", markdown, 1.0));

        Map<String, Object> out = suggester.suggest(
                "用户注册", "CHN_DRAFT", List.of("compA"), chunks, (code, data) -> true);

        assertEquals("executor-pattern:p1", out.get("source"));
        assertNotNull(out.get("proposedChainData"));
        assertTrue(((Map<?, ?>) out.get("validation")).get("valid").equals(true));
    }

    @Test
    void suggestWithLlm_usesMockClient() {
        ExecutorAiProperties props = new ExecutorAiProperties();
        props.setLlmEnabled(true);
        props.setBaseUrl("http://localhost:11434/v1");
        props.setModel("test-model");
        props.setRepairMaxRounds(0);

        ExecutorOpenAiClient client = mock(ExecutorOpenAiClient.class);
        String llmJson = """
                {"summary":"LLM链","chainData":{"nodes":[
                  {"id":"s","type":"START","label":"开始"},
                  {"id":"v","type":"NORMAL","label":"校验"},
                  {"id":"c","type":"CONDITION","label":"分支"},
                  {"id":"r","type":"NORMAL","label":"创建"},
                  {"id":"e","type":"END","label":"结束"}
                ],"edges":[
                  {"source":"s","target":"v"},
                  {"source":"v","target":"c"},
                  {"source":"c","target":"r","label":"False"},
                  {"source":"c","target":"e","label":"True"},
                  {"source":"r","target":"e"}
                ]}}
                """;
        when(client.chat(anyList(), any())).thenReturn(llmJson);

        ExecutorChainSuggester suggester = new ExecutorChainSuggester(props, client);
        Map<String, Object> out = suggester.suggest(
                "用户注册", "CHN_DRAFT", List.of(), List.of(), (code, data) -> true);

        assertEquals("executor-llm", out.get("source"));
        assertTrue(out.get("proposedChainData").toString().contains("CONDITION"));
    }
}
