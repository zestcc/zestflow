package com.zestflow.admin.service.log;

import com.zestflow.common.model.dto.ChainEvent;
import com.zestflow.common.protocol.ExecutionTrace;

/**
 * 执行轨迹实时流辅助 — 判断链执行是否已终态。
 */
public final class ExecutionTraceLiveSupport {

    private ExecutionTraceLiveSupport() {
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
