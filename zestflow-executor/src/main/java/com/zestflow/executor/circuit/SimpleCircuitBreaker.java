package com.zestflow.executor.circuit;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 简单熔断器 — 基于时间窗口的计数熔断
 * <p>
 * 状态流转：CLOSED → OPEN → HALF_OPEN → CLOSED（恢复） / OPEN（再次失败）
 */
@Slf4j
public class SimpleCircuitBreaker {

    /** 节点 ID，仅用于日志 */
    private final String nodeId;

    /** 失败阈值 */
    private final int failureThreshold;

    /** 恢复时间（毫秒） */
    private final long recoveryTimeoutMs;

    /** 半开状态下允许的试探请求数 */
    private static final int HALF_OPEN_MAX_CALLS = 1;

    /** 当前状态 */
    private final AtomicReference<CircuitState> state = new AtomicReference<>(CircuitState.CLOSED);

    /** 连续失败计数 */
    private final AtomicInteger failureCount = new AtomicInteger(0);

    /** 上次熔断时间戳 */
    private final AtomicLong lastFailureTime = new AtomicLong(0);

    /** 半开已用试探数 */
    private final AtomicInteger halfOpenCalls = new AtomicInteger(0);

    public SimpleCircuitBreaker(String nodeId, int failureThreshold, long recoveryTimeoutMs) {
        this.nodeId = nodeId;
        this.failureThreshold = failureThreshold;
        this.recoveryTimeoutMs = recoveryTimeoutMs;
    }

    /**
     * 检查请求是否允许通过
     */
    public boolean tryAcquire() {
        CircuitState current = state.get();

        if (current == CircuitState.CLOSED) {
            return true;
        }

        if (current == CircuitState.OPEN) {
            if (System.currentTimeMillis() - lastFailureTime.get() >= recoveryTimeoutMs) {
                // 恢复时间已到，尝试半开
                if (state.compareAndSet(CircuitState.OPEN, CircuitState.HALF_OPEN)) {
                    halfOpenCalls.set(0);
                    log.info("熔断器进入半开状态 nodeId={}", nodeId);
                    return tryHalfOpen();
                }
            }
            return false;
        }

        // HALF_OPEN
        return tryHalfOpen();
    }

    /**
     * 记录成功
     */
    public void onSuccess() {
        CircuitState prev = state.getAndSet(CircuitState.CLOSED);
        failureCount.set(0);
        halfOpenCalls.set(0);
        if (prev != CircuitState.CLOSED) {
            log.info("熔断器恢复闭合 nodeId={}", nodeId);
        }
    }

    /**
     * 记录失败
     */
    public void onFailure() {
        lastFailureTime.set(System.currentTimeMillis());
        if (state.get() == CircuitState.HALF_OPEN) {
            state.set(CircuitState.OPEN);
            log.warn("半开状态请求失败，熔断器重新断开 nodeId={}", nodeId);
            return;
        }

        int count = failureCount.incrementAndGet();
        if (count >= failureThreshold) {
            state.set(CircuitState.OPEN);
            log.warn("熔断器触发 nodeId={} failureCount={} threshold={}", nodeId, count, failureThreshold);
        }
    }

    public CircuitState getState() {
        return state.get();
    }

    private boolean tryHalfOpen() {
        if (halfOpenCalls.incrementAndGet() <= HALF_OPEN_MAX_CALLS) {
            return true;
        }
        return false;
    }
}
