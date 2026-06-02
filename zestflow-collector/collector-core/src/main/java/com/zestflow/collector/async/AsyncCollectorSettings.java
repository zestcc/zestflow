package com.zestflow.collector.async;

/**
 * 异步采集器运行时参数（与具体存储实现解耦，供 JDBC/Kafka/Rabbit 等复用）。
 */
public record AsyncCollectorSettings(
        int batchSize,
        int batchMaxWaitMs,
        int queueCapacity,
        boolean diskFallbackEnabled,
        String diskFallbackDir,
        int circuitBreakerThreshold,
        int circuitBreakerCooldownMs,
        long shutdownTimeoutMs,
        long diskReplayIntervalMs
) {
}
