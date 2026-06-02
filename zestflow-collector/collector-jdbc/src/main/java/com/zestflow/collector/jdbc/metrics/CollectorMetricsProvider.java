package com.zestflow.collector.jdbc.metrics;

import com.zestflow.collector.async.AsyncEventCollector;
import com.zestflow.collector.jdbc.config.CollectorProperties;
import com.zestflow.common.spi.EventCollector;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 从 {@link AsyncEventCollector} 读取队列/熔断指标。
 */
public class CollectorMetricsProvider {

    private final EventCollector eventCollector;
    private final CollectorProperties properties;

    public CollectorMetricsProvider(EventCollector eventCollector, CollectorProperties properties) {
        this.eventCollector = eventCollector;
        this.properties = properties;
    }

    public Optional<CollectorMetricsSnapshot> snapshot() {
        if (!(eventCollector instanceof AsyncEventCollector async)) {
            return Optional.empty();
        }
        return Optional.of(CollectorMetricsSnapshot.builder()
                .queueSize(async.getQueueSize())
                .queueCapacity(properties.getQueueCapacity())
                .publishedCount(async.getPublishedCount())
                .droppedCount(async.getDroppedCount())
                .circuitOpen(async.isCircuitOpen())
                .diskSpoolPending(async.getDiskSpoolPending())
                .build());
    }

    /** 供 /collector/health JSON 响应使用 */
    public Map<String, Object> healthDetails() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("status", "UP");
        if (eventCollector == null) {
            return details;
        }
        snapshot().ifPresent(s -> {
            details.put("queueSize", s.getQueueSize());
            details.put("queueCapacity", s.getQueueCapacity());
            details.put("publishedCount", s.getPublishedCount());
            details.put("droppedCount", s.getDroppedCount());
            details.put("circuitOpen", s.isCircuitOpen());
            details.put("diskSpoolPending", s.getDiskSpoolPending());
        });
        return details;
    }
}
