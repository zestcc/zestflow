package com.zestflow.executor.benchmark;

import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.executor.engine.DefaultChainExecutionEngine;
import com.zestflow.executor.engine.support.EngineTestFixtures;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 执行引擎编排层 JMH 微基准（mock 节点零业务耗时）。
 * <p>
 * 节点规模：1 / 10 / 50，对应 Phase 2c 压测门禁。
 */
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class EngineOrchestrationBenchmark {

    @State(Scope.Benchmark)
    public static class OneNodeState {
        DefaultChainExecutionEngine engine;

        @Setup(Level.Trial)
        public void setup() {
            engine = EngineTestFixtures.instantEngineForLinearChain(1);
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            if (engine != null) {
                engine.destroy();
            }
        }
    }

    @State(Scope.Benchmark)
    public static class TenNodeState {
        DefaultChainExecutionEngine engine;

        @Setup(Level.Trial)
        public void setup() {
            engine = EngineTestFixtures.instantEngineForLinearChain(10);
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            if (engine != null) {
                engine.destroy();
            }
        }
    }

    @State(Scope.Benchmark)
    public static class FiftyNodeState {
        DefaultChainExecutionEngine engine;

        @Setup(Level.Trial)
        public void setup() {
            engine = EngineTestFixtures.instantEngineForLinearChain(50);
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            if (engine != null) {
                engine.destroy();
            }
        }
    }

    @Benchmark
    public ChainExecuteResultDTO linear1Node(OneNodeState state) {
        return state.engine.execute("perf-linear-1", Map.of());
    }

    @Benchmark
    public ChainExecuteResultDTO linear10Nodes(TenNodeState state) {
        return state.engine.execute("perf-linear-10", Map.of());
    }

    @Benchmark
    public ChainExecuteResultDTO linear50Nodes(FiftyNodeState state) {
        return state.engine.execute("perf-linear-50", Map.of());
    }
}
