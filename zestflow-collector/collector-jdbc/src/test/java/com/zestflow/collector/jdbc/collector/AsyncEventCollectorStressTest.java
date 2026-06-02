package com.zestflow.collector.jdbc.collector;

import com.zestflow.collector.async.AsyncEventCollector;
import com.zestflow.collector.jdbc.config.CollectorProperties;
import com.zestflow.common.model.dto.ChainEvent;
import com.zestflow.common.spi.EventCollector;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2b P-03 — 8192 队列灌满 + 熔断/恢复门禁（对标 Resilience4j 熔断语义）。
 */
@Tag("perf")
class AsyncEventCollectorStressTest {

    @Test
    void queueSaturation_dropsOverflow_andRecoversAfterDrain() throws Exception {
        CollectorProperties props = new CollectorProperties();
        props.setQueueCapacity(8192);
        props.setBatchSize(200);
        props.setBatchMaxWaitMs(50);
        props.setCircuitBreakerThreshold(3);
        props.setCircuitBreakerCooldownMs(500);
        props.setDiskFallbackEnabled(false);

        SlowBatchDelegate delegate = new SlowBatchDelegate(200);
        AsyncEventCollector collector = new AsyncEventCollector(delegate, props.toAsyncSettings());

        int floodCount = 10_000;
        for (int i = 0; i < floodCount; i++) {
            collector.collect(sampleEvent("evt-" + i));
        }

        assertThat(collector.getQueueSize()).isLessThanOrEqualTo(props.getQueueCapacity());
        assertThat(collector.getDroppedCount()).isGreaterThan(0);

        delegate.release();
        assertThat(awaitQueueDrain(collector, 15)).isTrue();
        assertThat(collector.getQueueSize()).isZero();
        assertThat(collector.isCircuitOpen()).isFalse();

        collector.destroy();
    }

    private static boolean awaitQueueDrain(AsyncEventCollector collector, int timeoutSec) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (collector.getQueueSize() == 0) {
                return true;
            }
            Thread.sleep(100);
        }
        return collector.getQueueSize() == 0;
    }

    private static ChainEvent sampleEvent(String id) {
        return ChainEvent.builder()
                .eventId(id)
                .chainId("chain-stress")
                .eventType(ChainEvent.EventType.NODE_STARTED)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /** 模拟 JDBC 批量写入慢路径，使队列积压 */
    static class SlowBatchDelegate implements EventCollector {
        private final long delayMs;
        private volatile boolean released;
        private final CountDownLatch firstBatch = new CountDownLatch(1);
        private final AtomicInteger batchCount = new AtomicInteger(0);

        SlowBatchDelegate(long delayMs) {
            this.delayMs = delayMs;
        }

        void release() {
            released = true;
        }

        @Override
        public void collect(ChainEvent event) {
            collectBatch(List.of(event));
        }

        @Override
        public void collectBatch(List<ChainEvent> events) {
            firstBatch.countDown();
            while (!released) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            batchCount.addAndGet(events.size());
        }

        @Override
        public String getName() {
            return "slow-batch";
        }
    }
}
