package com.zestflow.admin.service.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.admin.client.CollectorQueryAggregator;
import com.zestflow.common.protocol.ExecutionTrace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionLiveStreamServiceTest {

    @Mock
    private CollectorQueryAggregator collectorQueryAggregator;

    private ExecutionLiveStreamService service;

    @BeforeEach
    void setUp() {
        service = new ExecutionLiveStreamService(collectorQueryAggregator, new ObjectMapper());
    }

    @Test
    void stream_queriesCollectorForTerminalTrace() throws Exception {
        ExecutionTrace trace = ExecutionTrace.builder()
                .executionId("exec-1")
                .status(1)
                .build();
        when(collectorQueryAggregator.getExecutionTrace("exec-1", "demo-app")).thenReturn(trace);

        service.stream("exec-1", "demo-app");
        Thread.sleep(800);

        verify(collectorQueryAggregator, atLeastOnce()).getExecutionTrace("exec-1", "demo-app");
    }

    @Test
    void stream_returnsEmitterWithConfiguredTimeout() {
        SseEmitter emitter = service.stream("exec-2", null);

        assertThat(emitter).isNotNull();
        assertThat(emitter.getTimeout()).isEqualTo(600_000L);
    }
}
