package com.zestflow.collector.async;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 采集器异步/降级配置 — 绑定 {@code zestflow.collector.*} 中与异步相关的字段。
 * <p>
 * 可与 JDBC 模块的 {@code CollectorProperties} 并存，Spring Boot 会合并同一前缀下的属性。
 */
@ConfigurationProperties(prefix = "zestflow.collector")
public class CollectorAsyncProperties {

    private int batchSize = 200;
    private int batchMaxWaitMs = 500;
    private int queueCapacity = 8192;
    private boolean diskFallbackEnabled = false;
    private String diskFallbackDir = "./collector-fallback";
    private boolean asyncEnabled = true;
    private int circuitBreakerThreshold = 10;
    private int circuitBreakerCooldownMs = 30_000;
    private long shutdownTimeoutMs = 5000;
    private long diskReplayIntervalMs = 5000;

    public AsyncCollectorSettings toSettings() {
        return new AsyncCollectorSettings(
                batchSize, batchMaxWaitMs, queueCapacity,
                diskFallbackEnabled, diskFallbackDir,
                circuitBreakerThreshold, circuitBreakerCooldownMs,
                shutdownTimeoutMs, diskReplayIntervalMs);
    }

    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getBatchMaxWaitMs() { return batchMaxWaitMs; }
    public void setBatchMaxWaitMs(int batchMaxWaitMs) { this.batchMaxWaitMs = batchMaxWaitMs; }
    public int getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
    public boolean isDiskFallbackEnabled() { return diskFallbackEnabled; }
    public void setDiskFallbackEnabled(boolean diskFallbackEnabled) { this.diskFallbackEnabled = diskFallbackEnabled; }
    public String getDiskFallbackDir() { return diskFallbackDir; }
    public void setDiskFallbackDir(String diskFallbackDir) { this.diskFallbackDir = diskFallbackDir; }
    public boolean isAsyncEnabled() { return asyncEnabled; }
    public void setAsyncEnabled(boolean asyncEnabled) { this.asyncEnabled = asyncEnabled; }
    public int getCircuitBreakerThreshold() { return circuitBreakerThreshold; }
    public void setCircuitBreakerThreshold(int circuitBreakerThreshold) { this.circuitBreakerThreshold = circuitBreakerThreshold; }
    public int getCircuitBreakerCooldownMs() { return circuitBreakerCooldownMs; }
    public void setCircuitBreakerCooldownMs(int circuitBreakerCooldownMs) { this.circuitBreakerCooldownMs = circuitBreakerCooldownMs; }
    public long getShutdownTimeoutMs() { return shutdownTimeoutMs; }
    public void setShutdownTimeoutMs(long shutdownTimeoutMs) { this.shutdownTimeoutMs = shutdownTimeoutMs; }
    public long getDiskReplayIntervalMs() { return diskReplayIntervalMs; }
    public void setDiskReplayIntervalMs(long diskReplayIntervalMs) { this.diskReplayIntervalMs = diskReplayIntervalMs; }
}
