package com.zestflow.common.registry;

import com.zestflow.common.constant.RegistryConstants;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 心跳连续失败计数 — 仅达到阈值后才降级为重新注册（对标 xxl-job / Nacos 瞬时网络容错）。
 */
public final class HeartbeatFailureTracker {

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    public void onSuccess() {
        consecutiveFailures.set(0);
    }

    /**
     * @return {@code true} 表示已连续失败达到阈值，调用方应降级为 register
     */
    public boolean onFailure() {
        return consecutiveFailures.incrementAndGet()
                >= RegistryConstants.HEARTBEAT_FAILURE_THRESHOLD_BEFORE_REREGISTER;
    }

    public int consecutiveFailures() {
        return consecutiveFailures.get();
    }

    public void reset() {
        consecutiveFailures.set(0);
    }
}
