package com.zestflow.executor.metrics;

import com.zestflow.executor.chain.ChainManager;
import com.zestflow.executor.interceptor.MetricsInterceptor;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;

/**
 * Executor Micrometer 指标 — 对标 Spring Boot Actuator / Micrometer 标准绑定。
 */
@RequiredArgsConstructor
public class ExecutorMicrometerBinder implements MeterBinder {

    private final MetricsInterceptor metricsInterceptor;
    private final ChainManager chainManager;

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("zestflow.executor.chains.loaded", chainManager, cm -> cm.getActiveCodes().size())
                .description("当前内存中已加载的链数量")
                .register(registry);
        Gauge.builder("zestflow.executor.executions.total", metricsInterceptor, MetricsInterceptor::getTotalChainInvocations)
                .description("链执行总次数")
                .register(registry);
        Gauge.builder("zestflow.executor.executions.success", metricsInterceptor, MetricsInterceptor::getTotalChainSuccesses)
                .description("链执行成功次数")
                .register(registry);
        Gauge.builder("zestflow.executor.executions.failure", metricsInterceptor, MetricsInterceptor::getTotalChainFailures)
                .description("链执行失败次数")
                .register(registry);
        Gauge.builder("zestflow.executor.executions.cost.ms", metricsInterceptor, MetricsInterceptor::getTotalChainCostMs)
                .description("链执行累计耗时（毫秒）")
                .register(registry);
    }
}
