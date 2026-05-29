package com.zestflow.collector.jdbc.collector;

import com.zestflow.collector.jdbc.entity.ChainEventPO;
import com.zestflow.collector.jdbc.mapper.ChainEventMapper;
import com.zestflow.collector.spi.EventCollector;
import com.zestflow.common.model.dto.ChainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

/**
 * JDBC 事件采集器 — 批量写入 chain_event 表
 * <p>
 * 幂等性保障：依赖 uk_event_id 唯一约束 + INSERT IGNORE，相同 eventId 重复写入不产生重复数据。
 */
@Slf4j
@RequiredArgsConstructor
public class JdbcEventCollector implements EventCollector {

    private final ChainEventMapper chainEventMapper;

    @Override
    public void collect(ChainEvent event) {
        chainEventMapper.insert(toPO(event));
    }

    @Override
    public void collectBatch(List<ChainEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        List<ChainEventPO> pos = events.stream()
                .map(JdbcEventCollector::toPO)
                .collect(Collectors.toList());
        int rows = chainEventMapper.insertIgnoreBatch(pos);
        if (rows < events.size()) {
            log.warn("批量写入去重 {} 条，实际写入 {} 条", events.size(), rows);
        }
    }

    static ChainEventPO toPO(ChainEvent event) {
        return ChainEventPO.builder()
                .eventId(event.getEventId())
                .eventType(event.getEventType().name())
                .chainId(event.getChainId())
                .chainName(event.getChainName())
                .nodeId(event.getNodeId())
                .nodeName(event.getNodeName())
                .executorId(event.getExecutorId())
                .appName(event.getAppName())
                .params(event.getParams())
                .result(event.getResult())
                .errorMessage(event.getErrorMessage())
                .costMs(event.getCostMs())
                .status(event.getStatus())
                .timestamp(event.getTimestamp())
                .metadata(event.getMetadata())
                .build();
    }
}
