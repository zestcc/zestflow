package com.zestflow.executor.event;

import com.zestflow.common.model.dto.ChainEvent;

/**
 * 链执行事件发布接口 — {@link #publish(ChainEvent)} 应在 ≤1ms 内返回（异步入队）。
 */
public interface EventPublisher {

    EventPublisher NOOP = event -> { };

    void publish(ChainEvent event);

    static EventPublisher noop() {
        return NOOP;
    }
}
