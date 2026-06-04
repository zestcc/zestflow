package com.zestflow.collector.jdbc.collector;

import com.zestflow.collector.jdbc.entity.ChainEventPO;
import com.zestflow.collector.jdbc.entity.ExecutionPayloadPO;
import com.zestflow.collector.jdbc.mapper.ChainEventMapper;
import com.zestflow.collector.jdbc.mapper.ExecutionPayloadMapper;
import com.zestflow.common.spi.EventCollector;
import com.zestflow.common.model.dto.ChainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

/**
 * JDBC 事件采集器 — 索引表 + 载荷表双写
 * <p>
 * 幂等性保障：依赖 uk_event_id 唯一约束 + INSERT IGNORE。
 */
@Slf4j
@RequiredArgsConstructor
public class JdbcEventCollector implements EventCollector {

    private final ChainEventMapper chainEventMapper;
    private final ExecutionPayloadMapper executionPayloadMapper;

    @Override
    public void collect(ChainEvent event) {
        collectBatch(List.of(event));
    }

    @Override
    public void collectBatch(List<ChainEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        List<ChainEventPO> indexRows = events.stream()
                .map(JdbcEventCollector::toIndexPO)
                .collect(Collectors.toList());
        List<ExecutionPayloadPO> payloadRows = events.stream()
                .map(JdbcEventCollector::toPayloadPO)
                .filter(JdbcEventCollector::hasPayload)
                .collect(Collectors.toList());

        int rows = chainEventMapper.insertIgnoreBatch(indexRows);
        if (rows < events.size()) {
            log.warn("批量写入索引去重 {} 条，实际写入 {} 条", events.size(), rows);
        }
        if (!payloadRows.isEmpty()) {
            executionPayloadMapper.insertIgnoreBatch(payloadRows);
        }
    }

    private static boolean hasPayload(ExecutionPayloadPO p) {
        return (p.getParams() != null && !p.getParams().isEmpty())
                || (p.getResult() != null && !p.getResult().isEmpty())
                || (p.getErrorMessage() != null && !p.getErrorMessage().isEmpty());
    }

    static ChainEventPO toIndexPO(ChainEvent event) {
        return ChainEventPO.builder()
                .eventId(event.getEventId())
                .eventType(event.getEventType().name())
                .executionId(event.getExecutionId())
                .chainId(event.getChainId())
                .chainName(event.getChainName())
                .nodeId(event.getNodeId())
                .nodeName(event.getNodeName())
                .executorId(event.getExecutorId())
                .appCode(event.getAppCode())
                .appName(event.getAppName())
                .tenantId(event.getTenantId())
                .costMs(event.getCostMs())
                .status(event.getStatus())
                .timestamp(event.getTimestamp())
                .metadata(event.getMetadata())
                .build();
    }

    static ExecutionPayloadPO toPayloadPO(ChainEvent event) {
        return ExecutionPayloadPO.builder()
                .refId(event.getEventId())
                .refType(ExecutionPayloadPO.REF_CHAIN_EVENT)
                .executionId(event.getExecutionId())
                .params(event.getParams())
                .result(event.getResult())
                .errorMessage(event.getErrorMessage())
                .build();
    }
}
