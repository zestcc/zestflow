package com.zestflow.collector.jdbc.metrics;

import lombok.Builder;
import lombok.Value;

/**
 * Collector 运行时指标快照（供 /collector/health 与 Micrometer 共用）。
 */
@Value
@Builder
public class CollectorMetricsSnapshot {

    int queueSize;
    int queueCapacity;
    int publishedCount;
    int droppedCount;
    boolean circuitOpen;
    int diskSpoolPending;
}
