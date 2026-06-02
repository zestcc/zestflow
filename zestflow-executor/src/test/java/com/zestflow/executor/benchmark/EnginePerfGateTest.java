package com.zestflow.executor.benchmark;

import com.zestflow.common.util.LatencyPercentiles;
import com.zestflow.executor.engine.DefaultChainExecutionEngine;
import com.zestflow.executor.engine.support.EngineTestFixtures;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2c — 引擎编排层 P99.9 门禁（mock 节点，JUnit 微基准）。
 * <p>
 * 手动 JMH 深度分析见 {@link EngineOrchestrationBenchmark}。
 */
@Tag("perf")
class EnginePerfGateTest {

    private static final int WARMUP = 100;
    private static final int SAMPLES = 500;

    @Test
    void linear1NodeP999WithinGate() {
        assertOrchestrationP999(1, 5L);
    }

    @Test
    void linear10NodesP999WithinGate() {
        assertOrchestrationP999(10, 30L);
    }

    @Test
    void linear50NodesP999WithinGate() {
        assertOrchestrationP999(50, 200L);
    }

    private static void assertOrchestrationP999(int nodeCount, long limitMs) {
        DefaultChainExecutionEngine engine = EngineTestFixtures.instantEngineForLinearChain(nodeCount);
        String code = "perf-linear-" + nodeCount;
        try {
            for (int i = 0; i < WARMUP; i++) {
                engine.execute(code, Map.of());
            }

            long[] samples = new long[SAMPLES];
            for (int i = 0; i < SAMPLES; i++) {
                long start = System.nanoTime();
                engine.execute(code, Map.of());
                samples[i] = Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            }

            LatencyPercentiles stats = LatencyPercentiles.fromSorted(samples);
            System.out.printf(Locale.ROOT,
                    "[perf-gate] linear-%d %s limit=%dms%n", nodeCount, stats, limitMs);

            assertThat(stats.p999Ms())
                    .as("linear-%d orchestration p999", nodeCount)
                    .isLessThanOrEqualTo(limitMs);
        } finally {
            engine.destroy();
        }
    }
}
