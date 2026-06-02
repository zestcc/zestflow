package com.zestflow.executor.interceptor;

/**
 * 链/节点指标导出 — 与 Micrometer 解耦，嵌入模式无 Actuator 时可用 NOOP。
 */
public interface ChainMetricsSink {

    ChainMetricsSink NOOP = new ChainMetricsSink() {
    };

    default void recordChainExecution(String chainCode, String outcome, long elapsedMs) {
    }

    default void recordNodeExecution(String nodeId, String outcome) {
    }
}
