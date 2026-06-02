package com.zestflow.collector.async.metrics;

import com.zestflow.collector.async.AsyncEventCollector;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;

/**
 * 采集器 Micrometer 指标 — 对标 Spring Boot Actuator / Resilience4j 可观测性。
 */
@RequiredArgsConstructor
public class AsyncCollectorMetricsBinder implements MeterBinder {

    private final AsyncEventCollector collector;

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("zestflow.collector.queue.size", collector, AsyncEventCollector::getQueueSize)
                .description("事件采集队列深度")
                .register(registry);
        Gauge.builder("zestflow.collector.events.published", collector, AsyncEventCollector::getPublishedCount)
                .description("已成功持久化的事件数")
                .register(registry);
        Gauge.builder("zestflow.collector.events.dropped", collector, AsyncEventCollector::getDroppedCount)
                .description("丢弃的事件数")
                .register(registry);
        Gauge.builder("zestflow.collector.circuit.open", collector, c -> c.isCircuitOpen() ? 1 : 0)
                .description("熔断器是否开启（1=开启）")
                .register(registry);
        Gauge.builder("zestflow.collector.disk.spool.pending", collector, AsyncEventCollector::getDiskSpoolPending)
                .description("磁盘降级待回放事件数")
                .register(registry);
    }
}
