package com.zestflow.admin.service.log;

import com.zestflow.common.model.dto.ChainEvent;
import com.zestflow.common.protocol.ExecutionTrace;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionTraceLiveSupportTest {

    @Test
    void isTerminal_whenStatusSuccess() {
        ExecutionTrace trace = ExecutionTrace.builder().status(1).build();
        assertThat(ExecutionTraceLiveSupport.isTerminal(trace)).isTrue();
    }

    @Test
    void isTerminal_whenChainCompletedEvent() {
        ExecutionTrace trace = ExecutionTrace.builder()
                .events(List.of(ChainEvent.builder().eventType(ChainEvent.EventType.CHAIN_COMPLETED).build()))
                .build();
        assertThat(ExecutionTraceLiveSupport.isTerminal(trace)).isTrue();
    }

    @Test
    void isTerminal_whenRunning() {
        ExecutionTrace trace = ExecutionTrace.builder()
                .events(List.of(ChainEvent.builder().eventType(ChainEvent.EventType.NODE_STARTED).build()))
                .build();
        assertThat(ExecutionTraceLiveSupport.isTerminal(trace)).isFalse();
    }

    @Test
    void fingerprint_changesWithEventCount() {
        ExecutionTrace t1 = ExecutionTrace.builder().eventCount(1).build();
        ExecutionTrace t2 = ExecutionTrace.builder().eventCount(2).build();
        assertThat(ExecutionTraceLiveSupport.fingerprint(t1))
                .isNotEqualTo(ExecutionTraceLiveSupport.fingerprint(t2));
    }
}
