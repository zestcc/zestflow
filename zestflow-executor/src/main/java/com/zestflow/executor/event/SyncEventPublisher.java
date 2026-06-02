package com.zestflow.executor.event;

import com.zestflow.common.model.dto.ChainEvent;
import com.zestflow.common.spi.EventCollector;

/**
 * 同步事件发布 — 直接调用 {@link EventCollector#collect(ChainEvent)}。
 */
public class SyncEventPublisher implements EventPublisher {

    private final EventCollector delegate;

    public SyncEventPublisher(EventCollector delegate) {
        this.delegate = delegate;
    }

    @Override
    public void publish(ChainEvent event) {
        if (event != null) {
            delegate.collect(event);
        }
    }
}
