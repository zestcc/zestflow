package com.zestflow.executor.metrics;

import com.zestflow.executor.interceptor.ChainMetricsSink;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;

/**
 * Micrometer 链/节点指标导出 — 仅在 classpath 存在 {@link MeterRegistry} 时装配。
 */
public class MicrometerChainMetricsSink implements ChainMetricsSink {

    private final MeterRegistry meterRegistry;

    public MicrometerChainMetricsSink(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void recordChainExecution(String chainCode, String outcome, long elapsedMs) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder("zestflow.chain.executions")
                .tag("chain", chainCode)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
        Timer.builder("zestflow.chain.duration")
                .tag("chain", chainCode)
                .tag("outcome", outcome)
                .publishPercentiles(0.5, 0.95, 0.99, 0.999)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(elapsedMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void recordNodeExecution(String nodeId, String outcome) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder("zestflow.node.executions")
                .tag("node", nodeId)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }
}
