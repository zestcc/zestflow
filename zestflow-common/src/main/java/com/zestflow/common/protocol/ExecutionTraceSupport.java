package com.zestflow.common.protocol;

import com.zestflow.common.model.dto.ChainEvent;

/**
 * 执行轨迹辅助 — 终态判定与 SSE 增量指纹（Admin / Collector 共用）。
 */
public final class ExecutionTraceSupport {

    private ExecutionTraceSupport() {
    }

    public static boolean isTerminal(ExecutionTrace trace) {
        if (trace == null) {
            return false;
        }
        Integer status = trace.getStatus();
        if (status != null && (status == 0 || status == 1)) {
            return true;
        }
        if (trace.getEvents() == null || trace.getEvents().isEmpty()) {
            return false;
        }
        for (ChainEvent event : trace.getEvents()) {
            if (event == null || event.getEventType() == null) {
                continue;
            }
            ChainEvent.EventType type = event.getEventType();
            if (type == ChainEvent.EventType.CHAIN_COMPLETED
                    || type == ChainEvent.EventType.CHAIN_FAILED
                    || type == ChainEvent.EventType.CHAIN_TIMEOUT) {
                return true;
            }
        }
        return false;
    }

    /** 用于 SSE 增量推送 — 事件数/状态变化即视为有更新 */
    public static int fingerprint(ExecutionTrace trace) {
        if (trace == null) {
            return 0;
        }
        return java.util.Objects.hash(
                trace.getEventCount(),
                trace.getStatus(),
                trace.getEndTime(),
                trace.getFailedCount(),
                trace.getSuccessCount());
    }
}
