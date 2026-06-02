package com.zestflow.executor.event;

import com.zestflow.collector.async.AsyncCollectorSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Executor 端事件发布配置 — {@code zestflow.executor.event.*}
 */
@ConfigurationProperties(prefix = "zestflow.executor.event")
public class ExecutorEventProperties {

    private boolean asyncEnabled = true;
    private int batchSize = 200;
    private int batchMaxWaitMs = 500;
    private int queueCapacity = 8192;
    private int offerTimeoutMs = 1;
    private boolean diskFallbackEnabled = false;
    private String diskFallbackDir = "./collector-fallback";
    private int circuitBreakerThreshold = 10;
    private int circuitBreakerCooldownMs = 30_000;
    private long shutdownTimeoutMs = 5000;
    private long diskReplayIntervalMs = 5000;
    /** 异步 drain 工作线程数（与 Collector 侧 pool-size 语义一致） */
    private int drainWorkerCount = 1;

    public AsyncCollectorSettings toSettings() {
        return new AsyncCollectorSettings(
                batchSize, batchMaxWaitMs, queueCapacity,
                diskFallbackEnabled, diskFallbackDir,
                circuitBreakerThreshold, circuitBreakerCooldownMs,
                shutdownTimeoutMs, diskReplayIntervalMs, drainWorkerCount);
    }

    public boolean isAsyncEnabled() {
        return asyncEnabled;
    }

    public void setAsyncEnabled(boolean asyncEnabled) {
        this.asyncEnabled = asyncEnabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getBatchMaxWaitMs() {
        return batchMaxWaitMs;
    }

    public void setBatchMaxWaitMs(int batchMaxWaitMs) {
        this.batchMaxWaitMs = batchMaxWaitMs;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public int getOfferTimeoutMs() {
        return offerTimeoutMs;
    }

    public void setOfferTimeoutMs(int offerTimeoutMs) {
        this.offerTimeoutMs = offerTimeoutMs;
    }

    public boolean isDiskFallbackEnabled() {
        return diskFallbackEnabled;
    }

    public void setDiskFallbackEnabled(boolean diskFallbackEnabled) {
        this.diskFallbackEnabled = diskFallbackEnabled;
    }

    public String getDiskFallbackDir() {
        return diskFallbackDir;
    }

    public void setDiskFallbackDir(String diskFallbackDir) {
        this.diskFallbackDir = diskFallbackDir;
    }

    public int getCircuitBreakerThreshold() {
        return circuitBreakerThreshold;
    }

    public void setCircuitBreakerThreshold(int circuitBreakerThreshold) {
        this.circuitBreakerThreshold = circuitBreakerThreshold;
    }

    public int getCircuitBreakerCooldownMs() {
        return circuitBreakerCooldownMs;
    }

    public void setCircuitBreakerCooldownMs(int circuitBreakerCooldownMs) {
        this.circuitBreakerCooldownMs = circuitBreakerCooldownMs;
    }

    public long getShutdownTimeoutMs() {
        return shutdownTimeoutMs;
    }

    public void setShutdownTimeoutMs(long shutdownTimeoutMs) {
        this.shutdownTimeoutMs = shutdownTimeoutMs;
    }

    public long getDiskReplayIntervalMs() {
        return diskReplayIntervalMs;
    }

    public void setDiskReplayIntervalMs(long diskReplayIntervalMs) {
        this.diskReplayIntervalMs = diskReplayIntervalMs;
    }

    public int getDrainWorkerCount() {
        return drainWorkerCount;
    }

    public void setDrainWorkerCount(int drainWorkerCount) {
        this.drainWorkerCount = drainWorkerCount;
    }
}
