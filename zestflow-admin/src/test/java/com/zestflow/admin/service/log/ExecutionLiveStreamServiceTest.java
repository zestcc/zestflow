package com.zestflow.admin.service.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.admin.client.CollectorQueryAggregator;
import com.zestflow.admin.config.LogLiveStreamProperties;
import com.zestflow.common.protocol.ExecutionTrace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionLiveStreamServiceTest {

    @Mock
    private CollectorQueryAggregator collectorQueryAggregator;

    private LogLiveStreamProperties properties;
    private ExecutionLiveStreamService service;

    @BeforeEach
    void setUp() {
        properties = new LogLiveStreamProperties();
        properties.setPollIntervalMs(50L);
        properties.setSseTimeoutMs(30_000L);
        properties.setPoolSize(2);
        service = new ExecutionLiveStreamService(collectorQueryAggregator, new ObjectMapper(), properties);
    }

    @Test
    void stream_queriesCollectorForTerminalTrace() throws Exception {
        ExecutionTrace trace = ExecutionTrace.builder()
                .executionId("exec-1")
                .status(1)
                .build();
        when(collectorQueryAggregator.getExecutionTrace("exec-1", "demo-app")).thenReturn(trace);

        service.stream("exec-1", "demo-app");
        Thread.sleep(300);

        verify(collectorQueryAggregator, atLeastOnce()).getExecutionTrace("exec-1", "demo-app");
    }

    @Test
    void stream_returnsEmitterWithConfiguredTimeout() {
        SseEmitter emitter = service.stream("exec-2", null);

        assertThat(emitter).isNotNull();
        assertThat(emitter.getTimeout()).isEqualTo(30_000L);
    }

    @Test
    void shutdownStreamExecutor_doesNotThrow() {
        assertThatCode(() -> service.shutdownStreamExecutor()).doesNotThrowAnyException();
    }

    @Test
    void stream_supportsConcurrentRequests() throws Exception {
        ExecutionTrace trace = ExecutionTrace.builder().executionId("exec-x").status(1).build();
        when(collectorQueryAggregator.getExecutionTrace(any(), any())).thenReturn(trace);

        int concurrency = 6;
        CountDownLatch started = new CountDownLatch(concurrency);
        ExecutorService clients = Executors.newFixedThreadPool(concurrency);
        try {
            for (int i = 0; i < concurrency; i++) {
                final int idx = i;
                clients.submit(() -> {
                    service.stream("exec-" + idx, "demo-app");
                    started.countDown();
                });
            }
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(400);
            verify(collectorQueryAggregator, atLeast(concurrency)).getExecutionTrace(any(), any());
        } finally {
            clients.shutdownNow();
            service.shutdownStreamExecutor();
        }
    }
}
