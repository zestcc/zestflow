package com.zestflow.common.protocol;

import com.zestflow.common.model.dto.ChainEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionTraceSupportTest {

    @Test
    void isTerminal_whenStatusSuccess() {
        ExecutionTrace trace = ExecutionTrace.builder().status(1).build();
        assertTrue(ExecutionTraceSupport.isTerminal(trace));
    }

    @Test
    void isTerminal_whenChainCompletedEvent() {
        ExecutionTrace trace = ExecutionTrace.builder()
                .events(List.of(ChainEvent.builder().eventType(ChainEvent.EventType.CHAIN_COMPLETED).build()))
                .build();
        assertTrue(ExecutionTraceSupport.isTerminal(trace));
    }

    @Test
    void isTerminal_whenRunning() {
        ExecutionTrace trace = ExecutionTrace.builder()
                .events(List.of(ChainEvent.builder().eventType(ChainEvent.EventType.NODE_STARTED).build()))
                .build();
        assertFalse(ExecutionTraceSupport.isTerminal(trace));
    }

    @Test
    void fingerprint_changesWithEventCount() {
        ExecutionTrace t1 = ExecutionTrace.builder().eventCount(1).build();
        ExecutionTrace t2 = ExecutionTrace.builder().eventCount(2).build();
        assertNotEquals(ExecutionTraceSupport.fingerprint(t1), ExecutionTraceSupport.fingerprint(t2));
    }
}
