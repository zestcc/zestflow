package com.zestflow.executor.event;

import com.zestflow.collector.async.AsyncCollectorSettings;
import com.zestflow.collector.async.DiskFallbackStore;
import com.zestflow.common.model.dto.ChainEvent;
import com.zestflow.common.spi.EventCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * 异步事件发布器 — 有界队列 → 批量 drain → {@link EventCollector#collectBatch(List)}。
 * <p>
 * 对标 Logstash persistent queue / RabbitMQ publisher confirm，保证链执行线程不被 Collector 阻塞。
 */
public class AsyncEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AsyncEventPublisher.class);

    private final BlockingQueue<ChainEvent> queue;
    private final EventCollector delegate;
    private final AsyncCollectorSettings config;
    private final int offerTimeoutMs;
    private final Thread workerThread;
    private final DiskFallbackStore diskFallbackStore;
    private volatile boolean running = true;
    private final PublishCircuitBreaker circuitBreaker;
    private final AtomicInteger publishedCount = new AtomicInteger(0);
    private final AtomicInteger droppedCount = new AtomicInteger(0);
    private final AtomicInteger replayedCount = new AtomicInteger(0);
    private long lastReplayAttemptMs = 0;

    public AsyncEventPublisher(EventCollector delegate, AsyncCollectorSettings config) {
        this(delegate, config, 1);
    }

    public AsyncEventPublisher(EventCollector delegate, AsyncCollectorSettings config, int offerTimeoutMs) {
        this.delegate = delegate;
        this.config = config;
        this.offerTimeoutMs = Math.max(0, offerTimeoutMs);
        this.queue = new LinkedBlockingQueue<>(config.queueCapacity());
        this.circuitBreaker = new PublishCircuitBreaker(config.circuitBreakerThreshold(),
                config.circuitBreakerCooldownMs());
        this.diskFallbackStore = config.diskFallbackEnabled()
                ? new DiskFallbackStore(config.diskFallbackDir()) : null;

        this.workerThread = new Thread(this::drainLoop, "zestflow-async-event-publisher");
        this.workerThread.setDaemon(true);
        this.workerThread.start();

        log.info("AsyncEventPublisher 启动 queueCapacity={} batchSize={} diskFallback={} collector={}",
                config.queueCapacity(), config.batchSize(), config.diskFallbackEnabled(), delegate.getName());
    }

    @Override
    public void publish(ChainEvent event) {
        if (!running || event == null) {
            return;
        }

        boolean offered;
        try {
            offered = offerTimeoutMs > 0
                    ? queue.offer(event, offerTimeoutMs, TimeUnit.MILLISECONDS)
                    : queue.offer(event);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        if (!offered) {
            droppedCount.incrementAndGet();
            if (diskFallbackStore != null) {
                diskFallbackStore.append(event);
            } else {
                log.warn("事件发布队列已满，丢弃事件 eventId={}", event.getEventId());
            }
        }
    }

    public void destroy() {
        log.info("AsyncEventPublisher 开始关闭...");
        running = false;
        LockSupport.unpark(workerThread);
        try {
            workerThread.join(config.shutdownTimeoutMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        drainBatch();
        int remaining = queue.size();
        if (remaining > 0) {
            if (diskFallbackStore != null) {
                queue.forEach(diskFallbackStore::append);
                queue.clear();
                log.warn("关闭时将 {} 条未发布事件写入磁盘降级", remaining);
            } else {
                log.warn("关闭时丢弃 {} 条未发布事件", remaining);
                queue.clear();
            }
        }
        log.info("AsyncEventPublisher 已关闭, 总发布={} 丢弃={} 回放={}",
                publishedCount.get(), droppedCount.get(), replayedCount.get());
    }

    private void drainLoop() {
        while (running) {
            try {
                tryReplayFromDisk();

                ChainEvent first = queue.poll(config.batchMaxWaitMs(), TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }

                List<ChainEvent> batch = new ArrayList<>(config.batchSize());
                batch.add(first);
                queue.drainTo(batch, config.batchSize() - 1);
                processBatch(batch);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("事件发布拉取循环异常", e);
            }
        }
    }

    private void tryReplayFromDisk() {
        if (diskFallbackStore == null || circuitBreaker.isOpen()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastReplayAttemptMs < config.diskReplayIntervalMs()) {
            return;
        }
        lastReplayAttemptMs = now;

        DiskFallbackStore.SpoolBatch spool = diskFallbackStore.pollBatch(config.batchSize());
        if (spool.events().isEmpty()) {
            return;
        }
        try {
            delegate.collectBatch(spool.events());
            publishedCount.addAndGet(spool.events().size());
            replayedCount.addAndGet(spool.events().size());
            diskFallbackStore.acknowledge(spool);
            circuitBreaker.recordSuccess();
            log.info("磁盘降级回放成功 size={}", spool.events().size());
        } catch (Exception e) {
            log.warn("磁盘降级回放失败，保留 spool 待下次重试 size={}", spool.events().size(), e);
            circuitBreaker.recordFailure();
        }
    }

    private void processBatch(List<ChainEvent> batch) {
        if (batch.isEmpty()) {
            return;
        }

        if (circuitBreaker.isOpen()) {
            if (diskFallbackStore != null) {
                batch.forEach(diskFallbackStore::append);
            } else {
                log.warn("熔断器开启，丢弃 {} 条事件", batch.size());
            }
            return;
        }

        try {
            delegate.collectBatch(batch);
            publishedCount.addAndGet(batch.size());
            log.debug("批量发布成功 collector={} size={}", delegate.getName(), batch.size());
            circuitBreaker.recordSuccess();
        } catch (Exception e) {
            log.error("批量发布失败 collector={} size={}", delegate.getName(), batch.size(), e);
            circuitBreaker.recordFailure();
            if (diskFallbackStore != null) {
                batch.forEach(diskFallbackStore::append);
            } else {
                requeueBatch(batch);
            }
        }
    }

    private void requeueBatch(List<ChainEvent> batch) {
        for (ChainEvent event : batch) {
            if (!queue.offer(event)) {
                droppedCount.incrementAndGet();
                log.warn("批量失败重入队时队列已满，丢弃事件 eventId={}", event.getEventId());
            }
        }
    }

    private void drainBatch() {
        List<ChainEvent> remaining = new ArrayList<>(config.batchSize());
        queue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            try {
                delegate.collectBatch(remaining);
                publishedCount.addAndGet(remaining.size());
            } catch (Exception e) {
                log.error("关闭时批量发布失败", e);
            }
        }
    }

    public int getPublishedCount() {
        return publishedCount.get();
    }

    public int getDroppedCount() {
        return droppedCount.get();
    }

    public int getReplayedCount() {
        return replayedCount.get();
    }

    public int getQueueSize() {
        return queue.size();
    }

    public boolean isCircuitOpen() {
        return circuitBreaker.isOpen();
    }

    static final class PublishCircuitBreaker {
        private final int threshold;
        private final long cooldownMs;
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private volatile long openedAt = 0;

        PublishCircuitBreaker(int threshold, long cooldownMs) {
            this.threshold = threshold;
            this.cooldownMs = cooldownMs;
        }

        boolean isOpen() {
            if (openedAt == 0) {
                return false;
            }
            if (System.currentTimeMillis() - openedAt > cooldownMs) {
                openedAt = 0;
                failureCount.set(0);
                return false;
            }
            return true;
        }

        void recordFailure() {
            int count = failureCount.incrementAndGet();
            if (count >= threshold) {
                openedAt = System.currentTimeMillis();
                log.warn("事件发布熔断器开启：连续 {} 次失败，冷却 {}ms", count, cooldownMs);
            }
        }

        void recordSuccess() {
            if (openedAt == 0) {
                failureCount.set(0);
            }
        }
    }
}
