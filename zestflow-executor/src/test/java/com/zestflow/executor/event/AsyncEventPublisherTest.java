package com.zestflow.executor.event;

import com.zestflow.collector.spi.EventCollector;
import com.zestflow.common.model.dto.ChainEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsyncEventPublisherTest {

    @Mock private EventCollector collector;
    @Captor private ArgumentCaptor<List<ChainEvent>> batchCaptor;

    private AsyncEventPublisher publisher;

    @BeforeEach
    void setUp() {
        AsyncEventPublisher.AsyncPublisherConfig config = AsyncEventPublisher.AsyncPublisherConfig.builder()
                .queueCapacity(1024)
                .batchSize(50)
                .batchMaxWaitMs(100)
                .circuitBreakerThreshold(10)
                .circuitBreakerCooldownMs(30000)
                .diskFallbackEnabled(false)
                .build();
        publisher = new AsyncEventPublisher(List.of(collector), config);
    }

    @AfterEach
    void tearDown() {
        publisher.destroy();
    }

    @Test
    void publishesSingleEvent() throws InterruptedException {
        ChainEvent event = createTestEvent(ChainEvent.EventType.CHAIN_STARTED);

        publisher.publish(event);

        Thread.sleep(400);
        verify(collector, atLeastOnce()).collectBatch(batchCaptor.capture());
        List<ChainEvent> captured = batchCaptor.getAllValues().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
        assertThat(captured).extracting(ChainEvent::getEventId).contains(event.getEventId());
    }

    @Test
    void publishesBatchOfEvents() throws InterruptedException {
        List<ChainEvent> events = IntStream.range(0, 10)
                .mapToObj(i -> createTestEvent(ChainEvent.EventType.NODE_COMPLETED))
                .collect(Collectors.toList());

        events.forEach(publisher::publish);

        Thread.sleep(400);
        verify(collector, atLeastOnce()).collectBatch(batchCaptor.capture());
        long deliveredCount = batchCaptor.getAllValues().stream()
                .flatMap(List::stream)
                .count();
        assertThat(deliveredCount).isGreaterThanOrEqualTo(10);
    }

    @Test
    void drainThreadFlushesOnDestroy() throws InterruptedException {
        ChainEvent event = createTestEvent(ChainEvent.EventType.CHAIN_COMPLETED);
        publisher.publish(event);

        publisher.destroy();

        verify(collector, atLeastOnce()).collectBatch(anyList());
    }

    @Test
    void doesNotBlockWhenQueueFull() {
        // 使用小队列测试丢弃行为
        AsyncEventPublisher.AsyncPublisherConfig smallConfig = AsyncEventPublisher.AsyncPublisherConfig.builder()
                .queueCapacity(8)
                .batchSize(50)
                .batchMaxWaitMs(100)
                .circuitBreakerThreshold(100)
                .circuitBreakerCooldownMs(30000)
                .diskFallbackEnabled(false)
                .build();
        AsyncEventPublisher smallPublisher = new AsyncEventPublisher(List.of(collector), smallConfig);

        // 快速发布大量事件，队列满后触发丢弃
        for (int i = 0; i < 100; i++) {
            smallPublisher.publish(createTestEvent(ChainEvent.EventType.NODE_STARTED));
        }

        assertThat(smallPublisher.getDroppedCount()).isGreaterThan(0);
        smallPublisher.destroy();
    }

    @Test
    void circuitBreakerOpensAfterFailures() throws InterruptedException {
        // 使用 batchSize=1 确保每个事件触发独立 processBatch() 调用
        AsyncEventPublisher.AsyncPublisherConfig smallBatchConfig = AsyncEventPublisher.AsyncPublisherConfig.builder()
                .queueCapacity(1024)
                .batchSize(1)
                .batchMaxWaitMs(10)
                .circuitBreakerThreshold(5)
                .circuitBreakerCooldownMs(30000)
                .diskFallbackEnabled(false)
                .build();
        AsyncEventPublisher localPublisher = new AsyncEventPublisher(List.of(collector), smallBatchConfig);

        doThrow(new RuntimeException("模拟失败")).when(collector).collectBatch(anyList());

        // 发布超过阈值的事件使熔断器打开（batchSize=1 → 15次processBatch → 15次recordFailure → 阈值5）
        for (int i = 0; i < 15; i++) {
            localPublisher.publish(createTestEvent(ChainEvent.EventType.NODE_STARTED));
        }

        Thread.sleep(1000);
        assertThat(localPublisher.isCircuitOpen()).isTrue();
        // 熔断器丢弃事件时不计入 droppedCount（只记队列满丢弃），但 publishedCount 应小于事件总数
        assertThat(localPublisher.getPublishedCount()).isLessThan(15);

        localPublisher.destroy();
    }

    private static ChainEvent createTestEvent(ChainEvent.EventType type) {
        return ChainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(type)
                .chainId("test-chain-" + UUID.randomUUID())
                .chainName("test-chain")
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
