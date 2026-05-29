package com.zestflow.executor.event;

import com.zestflow.collector.spi.EventCollector;
import com.zestflow.common.model.dto.ChainEvent;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * 异步事件发布器 — 三级流水线：内存队列 → 批量代理 → Collector
 * <p>
 * 设计要点：
 * <ul>
 *   <li>有界队列 + offer() 超时 ≤1ms，绝不阻塞业务线程</li>
 *   <li>独立线程批量拉取，按批次转发给所有注册的 EventCollector</li>
 *   <li>熔断器：连续失败 N 次后暂停采集一个冷却周期</li>
 *   <li>磁盘降级（可选）：队列满或熔断开启时写入本地文件，重启后回放</li>
 *   <li>优雅关闭：等待已入队事件消费完成</li>
 * </ul>
 */
@Slf4j
public class AsyncEventPublisher implements EventPublisher {

    /** 事件缓冲队列 */
    private final BlockingQueue<ChainEvent> queue;

    /** 采集器列表 */
    private final List<EventCollector> collectors;

    /** 配置参数 */
    private final AsyncPublisherConfig config;

    /** 后台批量拉取线程 */
    private final Thread workerThread;

    /** 关闭标记 */
    private volatile boolean running = true;

    /** 熔断器状态 */
    private final CircuitBreaker circuitBreaker;

    /** 统计计数器 */
    private final AtomicInteger publishedCount = new AtomicInteger(0);
    private final AtomicInteger droppedCount = new AtomicInteger(0);

    public AsyncEventPublisher(List<EventCollector> collectors, AsyncPublisherConfig config) {
        this.collectors = collectors;
        this.config = config;
        this.queue = new LinkedBlockingQueue<>(config.getQueueCapacity());
        this.circuitBreaker = new CircuitBreaker(config.getCircuitBreakerThreshold(),
                config.getCircuitBreakerCooldownMs());

        this.workerThread = new Thread(this::drainLoop, "zestflow-event-drain");
        this.workerThread.setDaemon(true);
        this.workerThread.start();

        log.info("AsyncEventPublisher 启动 queueCapacity={} batchSize={} collectors={}",
                config.getQueueCapacity(), config.getBatchSize(), collectors.size());
    }

    @Override
    public void publish(ChainEvent event) {
        if (!running || event == null) {
            return;
        }

        boolean offered = queue.offer(event);
        if (!offered) {
            droppedCount.incrementAndGet();
            // 队列满 → 磁盘降级（可选）
            if (config.isDiskFallbackEnabled()) {
                writeToDisk(event);
            } else {
                log.warn("事件队列已满，丢弃事件 eventId={}", event.getEventId());
            }
        }
    }

    @Override
    public void destroy() {
        log.info("AsyncEventPublisher 开始关闭...");
        running = false;
        // 唤醒工作线程
        LockSupport.unpark(workerThread);
        try {
            workerThread.join(config.getShutdownTimeoutMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // 最后尝试消费剩余事件
        drainBatch();
        int remaining = queue.size();
        if (remaining > 0) {
            log.warn("关闭时丢弃 {} 条未处理事件", remaining);
        }
        log.info("AsyncEventPublisher 已关闭, 总发布={} 丢弃={}",
                publishedCount.get(), droppedCount.get());
    }

    /**
     * 后台批量拉取循环
     */
    private void drainLoop() {
        while (running) {
            try {
                // 从队列拉取一批事件（等待首个事件到达）
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

    /**
     * 批量处理：转发给所有 Collector
     */
    private void processBatch(List<ChainEvent> batch) {
        if (batch.isEmpty()) {
            return;
        }

        // 熔断检测
        if (circuitBreaker.isOpen()) {
            if (config.isDiskFallbackEnabled()) {
                batch.forEach(this::writeToDisk);
            } else {
                log.warn("熔断器开启，丢弃 {} 条事件", batch.size());
            }
            return;
        }

        boolean allFailed = true;
        for (EventCollector collector : collectors) {
            try {
                collector.collectBatch(batch);
                allFailed = false;
                publishedCount.addAndGet(batch.size());
                log.debug("批量提交成功 collector={} size={}", collector.getName(), batch.size());
            } catch (Exception e) {
                log.error("Collector 批量提交失败 collector={} size={}",
                        collector.getName(), batch.size(), e);
            }
        }

        if (allFailed) {
            circuitBreaker.recordFailure();
            if (config.isDiskFallbackEnabled()) {
                batch.forEach(this::writeToDisk);
            }
        } else {
            circuitBreaker.recordSuccess();
        }
    }

    /**
     * 同步拉取并消费剩余事件（关闭时调用）
     */
    private void drainBatch() {
        List<ChainEvent> remaining = new ArrayList<>(config.getBatchSize());
        queue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            for (EventCollector collector : collectors) {
                try {
                    collector.collectBatch(remaining);
                } catch (Exception e) {
                    log.error("关闭时批量提交失败 collector={}", collector.getName(), e);
                }
            }
        }
    }

    /**
     * 写入磁盘降级文件
     */
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

    // ========== Getter ==========

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

    // ========== 内部类 ==========

    /**
     * 简单熔断器 — 连续失败 N 次后开启，冷却后关闭
     */
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

    /**
     * 发布器配置
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AsyncPublisherConfig {
        /** 队列容量 */
        private int queueCapacity = 8192;

        /** 批量大小 */
        private int batchSize = 200;

        /** 批量最大等待时间（毫秒） */
        private long batchMaxWaitMs = 500;

        /** 熔断阈值（连续失败次数） */
        private int circuitBreakerThreshold = 10;

        /** 熔断冷却时间（毫秒） */
        private long circuitBreakerCooldownMs = 30_000;

        /** 是否启用磁盘降级 */
        private boolean diskFallbackEnabled = false;

        /** 磁盘降级目录 */
        private String diskFallbackDir = "./collector-fallback";

        /** 关闭等待时间（毫秒） */
        private long shutdownTimeoutMs = 5000;
    }
}
