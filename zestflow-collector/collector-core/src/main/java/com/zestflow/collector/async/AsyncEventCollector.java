package com.zestflow.collector.async;

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
 * 异步事件采集器 — 内存队列 → 多 worker 批量 drain → 下游 Collector（对标 Logstash pipeline workers）。
 */
public class AsyncEventCollector implements EventCollector {

    private static final Logger log = LoggerFactory.getLogger(AsyncEventCollector.class);

    private final BlockingQueue<ChainEvent> queue;
    private final EventCollector delegate;
    private final AsyncCollectorSettings config;
    private final List<Thread> workerThreads;
    private final DiskFallbackStore diskFallbackStore;
    private volatile boolean running = true;
    private final CircuitBreaker circuitBreaker;
    private final AtomicInteger publishedCount = new AtomicInteger(0);
    private final AtomicInteger droppedCount = new AtomicInteger(0);
    private final AtomicInteger replayedCount = new AtomicInteger(0);
    private long lastReplayAttemptMs = 0;

    public AsyncEventCollector(EventCollector delegate, AsyncCollectorSettings config) {
        this.delegate = delegate;
        this.config = config;
        this.queue = new LinkedBlockingQueue<>(config.queueCapacity());
        this.circuitBreaker = new CircuitBreaker(config.circuitBreakerThreshold(),
                config.circuitBreakerCooldownMs());
        this.diskFallbackStore = config.diskFallbackEnabled()
                ? new DiskFallbackStore(config.diskFallbackDir()) : null;

        int workers = config.drainWorkerCount();
        this.workerThreads = new ArrayList<>(workers);
        for (int i = 0; i < workers; i++) {
            Thread worker = new Thread(this::drainLoop, "zestflow-async-collector-drain-" + i);
            worker.setDaemon(true);
            workerThreads.add(worker);
            worker.start();
        }

        log.info("AsyncEventCollector 启动 queueCapacity={} batchSize={} workers={} diskFallback={} delegate={}",
                config.queueCapacity(), config.batchSize(), workers, config.diskFallbackEnabled(), delegate.getName());
    }

    @Override
    public void collect(ChainEvent event) {
        if (!running || event == null) {
            return;
        }

        boolean offered = queue.offer(event);
        if (!offered) {
            droppedCount.incrementAndGet();
            if (diskFallbackStore != null) {
                diskFallbackStore.append(event);
            } else {
                log.warn("事件队列已满，丢弃事件 eventId={}", event.getEventId());
            }
        }
    }

    @Override
    public void collectBatch(List<ChainEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        for (ChainEvent event : events) {
            collect(event);
        }
    }

    public void destroy() {
        log.info("AsyncEventCollector 开始关闭...");
        running = false;
        for (Thread worker : workerThreads) {
            LockSupport.unpark(worker);
        }
        for (Thread worker : workerThreads) {
            try {
                worker.join(config.shutdownTimeoutMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        drainBatch();
        int remaining = queue.size();
        if (remaining > 0) {
            if (diskFallbackStore != null) {
                queue.forEach(diskFallbackStore::append);
                queue.clear();
                log.warn("关闭时将 {} 条未处理事件写入磁盘降级", remaining);
            } else {
                log.warn("关闭时丢弃 {} 条未处理事件", remaining);
                queue.clear();
            }
        }
        log.info("AsyncEventCollector 已关闭, 总处理={} 丢弃={} 回放={}",
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
                log.error("事件拉取循环异常", e);
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
        if (spool.isEmpty()) {
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
            log.debug("批量提交成功 collector={} size={}", delegate.getName(), batch.size());
            circuitBreaker.recordSuccess();
        } catch (Exception e) {
            log.error("批量提交失败 collector={} size={}", delegate.getName(), batch.size(), e);
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
            } catch (Exception e) {
                log.error("关闭时批量提交失败", e);
            }
        }
    }

    static class CircuitBreaker {
        private final int threshold;
        private final long cooldownMs;
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private volatile long openedAt = 0;

        CircuitBreaker(int threshold, long cooldownMs) {
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
                log.warn("熔断器开启：连续 {} 次失败，冷却 {}ms", count, cooldownMs);
            }
        }

        void recordSuccess() {
            if (openedAt == 0) {
                failureCount.set(0);
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

    public int getDiskSpoolPending() {
        return diskFallbackStore != null ? diskFallbackStore.countPendingEvents() : 0;
    }
}
