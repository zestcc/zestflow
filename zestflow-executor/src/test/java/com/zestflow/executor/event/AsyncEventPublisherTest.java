package com.zestflow.executor.event;

import com.zestflow.collector.async.AsyncCollectorSettings;
import com.zestflow.common.model.dto.ChainEvent;
import com.zestflow.common.spi.EventCollector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsyncEventPublisherTest {

    @Mock
    private EventCollector eventCollector;

    private AsyncEventPublisher publisher;

    private static AsyncCollectorSettings testSettings() {
        return new AsyncCollectorSettings(
                10, 50, 256,
                false, "./target/test-fallback",
                3, 200,
                3000, 1000);
    }

    @BeforeEach
    void setUp() {
        when(eventCollector.getName()).thenReturn("mock-collector");
        publisher = new AsyncEventPublisher(eventCollector, testSettings(), 1);
    }

    @AfterEach
    void tearDown() {
        if (publisher != null) {
            publisher.destroy();
        }
    }

    @Test
    void publishEventSuccessfully() throws InterruptedException {
        ChainEvent event = sampleEvent("evt-1");

        publisher.publish(event);

        verify(eventCollector, timeout(2000).atLeastOnce()).collectBatch(anyList());
    }

    @Test
    void publishWithBatchCollect() throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            publisher.publish(sampleEvent("evt-" + i));
        }

        ArgumentCaptor<List<ChainEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(eventCollector, timeout(2000).atLeastOnce()).collectBatch(captor.capture());
        assertThat(captor.getValue()).isNotEmpty();
    }

    @Test
    void circuitBreakerOpensAfterFailures() throws InterruptedException {
        doThrow(new RuntimeException("down")).when(eventCollector).collectBatch(anyList());

        for (int i = 0; i < 5; i++) {
            publisher.publish(sampleEvent("fail-" + i));
        }

        awaitTrue(() -> publisher.isCircuitOpen(), 3000);
        assertThat(publisher.isCircuitOpen()).isTrue();
    }

    @Test
    void circuitBreakerRecoversAfterCooldown() throws Exception {
        doThrow(new RuntimeException("down")).when(eventCollector).collectBatch(anyList());

        for (int i = 0; i < 5; i++) {
            publisher.publish(sampleEvent("fail-" + i));
        }
        awaitTrue(() -> publisher.isCircuitOpen(), 3000);

        TimeUnit.MILLISECONDS.sleep(250);

        org.mockito.Mockito.reset(eventCollector);
        when(eventCollector.getName()).thenReturn("mock-collector");

        publisher.publish(sampleEvent("recover"));
        verify(eventCollector, timeout(2000).atLeastOnce()).collectBatch(anyList());
        assertThat(publisher.isCircuitOpen()).isFalse();
    }

    @Test
    void shutdownDrainsRemainingEvents() {
        ChainEvent event = sampleEvent("shutdown-1");
        publisher.publish(event);

        publisher.destroy();

        verify(eventCollector, atLeastOnce()).collectBatch(anyList());
    }

    private static ChainEvent sampleEvent(String eventId) {
        return ChainEvent.builder()
                .eventId(eventId)
                .eventType(ChainEvent.EventType.NODE_COMPLETED)
                .chainId("chain-1")
                .timestamp(System.currentTimeMillis())
                .build();
    }

    private static void awaitTrue(java.util.function.BooleanSupplier condition, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
    }
}
