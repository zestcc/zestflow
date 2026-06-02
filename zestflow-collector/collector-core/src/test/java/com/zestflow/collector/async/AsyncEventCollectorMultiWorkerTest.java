package com.zestflow.collector.async;

import com.zestflow.common.model.dto.ChainEvent;
import com.zestflow.common.spi.EventCollector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncEventCollectorMultiWorkerTest {

    @Test
    void multipleWorkers_drainQueueFaster() throws Exception {
        AsyncCollectorSettings settings = new AsyncCollectorSettings(
                50, 20, 4096, false, "./fallback", 10, 30_000, 5000, 5000, 4);
        CountingDelegate delegate = new CountingDelegate();
        AsyncEventCollector collector = new AsyncEventCollector(delegate, settings);

        int eventCount = 800;
        for (int i = 0; i < eventCount; i++) {
            collector.collect(sampleEvent("evt-" + i));
        }

        assertTrue(awaitPublished(delegate, eventCount, 10));
        assertEquals(eventCount, delegate.getPublished());
        collector.destroy();
    }

    private static boolean awaitPublished(CountingDelegate delegate, int expected, int timeoutSec)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (delegate.getPublished() >= expected) {
                return true;
            }
            Thread.sleep(50);
        }
        return delegate.getPublished() >= expected;
    }

    private static ChainEvent sampleEvent(String id) {
        return ChainEvent.builder()
                .eventId(id)
                .chainId("chain-multi-worker")
                .eventType(ChainEvent.EventType.NODE_STARTED)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    static class CountingDelegate implements EventCollector {
        private final AtomicInteger published = new AtomicInteger(0);
        private final CountDownLatch batchLatch = new CountDownLatch(1);

        @Override
        public void collect(ChainEvent event) {
            published.incrementAndGet();
        }

        @Override
        public void collectBatch(List<ChainEvent> events) {
            published.addAndGet(events.size());
            batchLatch.countDown();
        }

        @Override
        public String getName() {
            return "counting";
        }

        int getPublished() {
            return published.get();
        }
    }
}
