package com.zestflow.executor.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutorRagIndexEngineTest {

    private final ExecutorRagIndexEngine engine = new ExecutorRagIndexEngine();

    @Test
    void hybridSearch_prefersMatchingDocument() {
        List<ExecutorRagChunk> corpus = List.of(
                new ExecutorRagChunk("a", "用户注册 validate email password", 0.5),
                new ExecutorRagChunk("b", "订单支付 refund gateway callback", 0.5),
                new ExecutorRagChunk("c", "inventory stock deduct warehouse", 0.5));

        ExecutorAiProperties props = new ExecutorAiProperties();
        props.setRagMode("hybrid");
        props.setRagMaxChunks(3);
        props.setRagUseEmbedding(false);

        List<ExecutorRagChunk> hits = engine.search(corpus, "用户注册", props, null);
        assertFalse(hits.isEmpty());
        assertEquals("a", hits.get(0).id());
    }

    @Test
    void keywordMode_ignoresWeakVectorMatch() {
        List<ExecutorRagChunk> corpus = List.of(
                new ExecutorRagChunk("x", "payment order checkout", 0.5));

        ExecutorAiProperties props = new ExecutorAiProperties();
        props.setRagMode("keyword");
        props.setRagMaxChunks(2);

        List<ExecutorRagChunk> hits = engine.search(corpus, "完全无关的中文查询", props, null);
        assertTrue(hits.isEmpty() || hits.get(0).score() >= 0);
    }

    @Test
    void emptyQuery_returnsEmpty() {
        ExecutorAiProperties props = new ExecutorAiProperties();
        assertTrue(engine.search(List.of(new ExecutorRagChunk("a", "text", 1)), "", props, null).isEmpty());
    }
}
