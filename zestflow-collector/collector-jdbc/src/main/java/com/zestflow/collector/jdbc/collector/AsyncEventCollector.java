package com.zestflow.collector.jdbc.collector;

import com.zestflow.collector.jdbc.config.CollectorProperties;
import com.zestflow.common.model.dto.ChainEvent;
import com.zestflow.common.spi.EventCollector;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * 异步事件采集器 — 三级流水线：内存队列 → 批量代理 → 下游 Collector
 * <p>
 * 装饰 {@link JdbcEventCollector} 或其他 {@link EventCollector} 实现，提供：
 * <ul>
 *   <li>有界队列 + offer() 非阻塞入队，绝不阻塞业务线程</li>
 *   <li>独立线程批量拉取，按批次转发给下游 Collector</li>
 *   <li>熔断器：连续失败 N 次后暂停采集一个冷却周期</li>
 *   <li>磁盘降级（可选）：队列满或熔断开启时写入本地文件</li>
 * </ul>
 */
@Slf4j
public class AsyncEventCollector implements EventCollector {

    private final BlockingQueue<ChainEvent> queue;

    private final EventCollector delegate;

    private final CollectorProperties config;

    private final Thread workerThread;

    private volatile boolean running = true;

    private final CircuitBreaker circuitBreaker;

    private final AtomicInteger publishedCount = new AtomicInteger(0);
    private final AtomicInteger droppedCount = new AtomicInteger(0);

    public AsyncEventCollector(EventCollector delegate, CollectorProperties config) {
        this.delegate = delegate;
        this.config = config;
        this.queue = new LinkedBlockingQueue<>(config.getQueueCapacity());
        this.circuitBreaker = new CircuitBreaker(config.getCircuitBreakerThreshold(),
                config.getCircuitBreakerCooldownMs());

        this.workerThread = new Thread(this::drainLoop, "zestflow-async-collector-drain");
        this.workerThread.setDaemon(true);
        this.workerThread.start();

        log.info("AsyncEventCollector 启动 queueCapacity={} batchSize={} delegate={}",
                config.getQueueCapacity(), config.getBatchSize(), delegate.getName());
    }

    @Override
    public void collect(ChainEvent event) {
        if (!running || event == null) {
            return;
        }

        boolean offered = queue.offer(event);
        if (!offered) {
            droppedCount.incrementAndGet();
            if (config.isDiskFallbackEnabled()) {
                writeToDisk(event);
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
        // 批量入口也逐个入队，由 drain 线程统一批量提交
        for (ChainEvent event : events) {
            collect(event);
        }
    }

    /**
     * 优雅关闭：等待已入队事件处理完成
     */
    public void destroy() {
        log.info("AsyncEventCollector 开始关闭...");
        running = false;
        LockSupport.unpark(workerThread);
        try {
            workerThread.join(config.getShutdownTimeoutMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        drainBatch();
        int remaining = queue.size();
        if (remaining > 0) {
            log.warn("关闭时丢弃 {} 条未处理事件", remaining);
        }
        log.info("AsyncEventCollector 已关闭, 总处理={} 丢弃={}",
                publishedCount.get(), droppedCount.get());
    }

    private void drainLoop() {
        while (running) {
            try {
                ChainEvent first = queue.poll(config.getBatchMaxWaitMs(), TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }

                List<ChainEvent> batch = new ArrayList<>(config.getBatchSize());
                batch.add(first);
                queue.drainTo(batch, config.getBatchSize() - 1);

                processBatch(batch);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("事件拉取循环异常", e);
            }
        }
    }

    private void processBatch(List<ChainEvent> batch) {
        if (batch.isEmpty()) {
            return;
        }

        if (circuitBreaker.isOpen()) {
            if (config.isDiskFallbackEnabled()) {
                batch.forEach(this::writeToDisk);
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
            if (config.isDiskFallbackEnabled()) {
                batch.forEach(this::writeToDisk);
            }
        }
    }

    private void drainBatch() {
        List<ChainEvent> remaining = new ArrayList<>(config.getBatchSize());
        queue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            try {
                delegate.collectBatch(remaining);
            } catch (Exception e) {
                log.error("关闭时批量提交失败", e);
            }
        }
    }

    private void writeToDisk(ChainEvent event) {
        try {
            Path dir = Paths.get(config.getDiskFallbackDir());
            Files.createDirectories(dir);
            String fileName = "events-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".log";
            Path file = dir.resolve(fileName);
            String line = event.getEventId() + "|" + event.getTimestamp() + "|"
                    + event.getEventType() + "|" + event.getChainId() + System.lineSeparator();
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("磁盘降级写入失败 eventId={}", event.getEventId(), e);
        }
    }

    // ========== 熔断器 ==========

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

    // ========== Getter（测试/监控用） ==========

    public int getPublishedCount() {
        return publishedCount.get();
    }

    public int getDroppedCount() {
        return droppedCount.get();
    }

    public int getQueueSize() {
        return queue.size();
    }

    public boolean isCircuitOpen() {
        return circuitBreaker.isOpen();
    }
}
