package com.zestflow.collector.async.metrics;

import com.zestflow.collector.async.AsyncEventCollector;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 采集器指标绑定辅助 — 各 transport 模块复用，避免重复造轮子。
 */
public final class CollectorMetricsSupport {

    private CollectorMetricsSupport() {
    }

    public static void bindIfAvailable(AsyncEventCollector async, MeterRegistry registry) {
        if (async == null || registry == null) {
            return;
        }
        new AsyncCollectorMetricsBinder(async).bindTo(registry);
    }
}
