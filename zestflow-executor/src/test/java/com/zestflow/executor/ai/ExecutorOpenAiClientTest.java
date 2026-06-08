package com.zestflow.executor.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutorOpenAiClientTest {

    @Test
    void parseEmbeddings_readsOpenAiFormat() {
        String json = """
                {"data":[
                  {"index":0,"embedding":[0.1,0.2,0.3]},
                  {"index":1,"embedding":[0.4,0.5,0.6]}
                ]}
                """;
        List<float[]> vectors = ExecutorOpenAiClient.parseEmbeddings(json, 2);
        assertEquals(2, vectors.size());
        assertEquals(3, vectors.get(0).length);
        assertTrue(vectors.get(1)[2] > 0.5f);
    }

    @Test
    void normalizeUrl_appendsV1Suffix() {
        assertEquals("http://localhost:11434/v1", ExecutorOpenAiClient.normalizeUrl("http://localhost:11434"));
        assertEquals("http://localhost:11434/v1", ExecutorOpenAiClient.normalizeUrl("http://localhost:11434/v1/"));
    }

    @Test
    void parseEmbeddings_emptyData_throws() {
        assertThrows(ExecutorAiException.class,
                () -> ExecutorOpenAiClient.parseEmbeddings("{\"data\":[]}", 1));
    }
}
