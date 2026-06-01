package com.zestflow.collector.jdbc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zestflow.collector.jdbc.entity.ChainEventPO;
import com.zestflow.collector.jdbc.mapper.ChainEventMapper;
import com.zestflow.common.protocol.EventQuery;
import com.zestflow.common.protocol.EventStats;
import com.zestflow.common.protocol.EventStatsQuery;
import com.zestflow.common.protocol.ExecutionTrace;
import com.zestflow.collector.spi.EventQueryService;
import com.zestflow.common.model.dto.ChainEvent;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JDBC 事件查询服务
 */
@RequiredArgsConstructor
public class JdbcEventQueryService implements EventQueryService {

    private final ChainEventMapper chainEventMapper;

    @Override
    public List<ChainEvent> queryEvents(EventQuery query) {
        IPage<ChainEventPO> page = new Page<>(query.getPage(), query.getPageSize());
        LambdaQueryWrapper<ChainEventPO> wrapper = buildQueryWrapper(query);
        wrapper.orderByDesc(ChainEventPO::getTimestamp);
        IPage<ChainEventPO> result = chainEventMapper.selectPage(page, wrapper);
        return result.getRecords().stream()
                .map(JdbcEventQueryService::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public long countEvents(EventQuery query) {
        LambdaQueryWrapper<ChainEventPO> wrapper = buildQueryWrapper(query);
        return chainEventMapper.selectCount(wrapper);
    }

    @Override
    public ChainEvent getById(String eventId) {
        LambdaQueryWrapper<ChainEventPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChainEventPO::getEventId, eventId);
        ChainEventPO po = chainEventMapper.selectOne(wrapper);
        return po != null ? toDTO(po) : null;
    }

    @Override
    public EventStats queryStats(EventStatsQuery query) {
        Map<String, Object> agg = chainEventMapper.selectAggregatedStats(query);
        Number totalCount = (Number) agg.getOrDefault("totalCount", 0L);
        Number avgCostMs = (Number) agg.getOrDefault("avgCostMs", 0D);
        Number successCount = (Number) agg.getOrDefault("successCount", 0L);
        Number failCount = (Number) agg.getOrDefault("failCount", 0L);

        long sc = successCount.longValue();
        long fc = failCount.longValue();
        double successRate = (sc + fc) > 0 ? (double) sc / (sc + fc) * 100.0 : 0.0;

        return EventStats.builder()
                .totalCount(totalCount.longValue())
                .avgCostMs(avgCostMs.doubleValue())
                .successRate(successRate)
                .failCount(fc)
                .build();
    }

    @Override
    public List<ExecutionTrace> queryExecutionTraces(EventQuery query) {
        int limit = query.getPageSize();
        int offset = (query.getPage() - 1) * limit;
        List<ChainEventPO> pos = chainEventMapper.selectExecutionTraces(query, limit, offset);
        return pos.stream()
                .map(JdbcEventQueryService::poToTraceSummary)
                .collect(Collectors.toList());
    }

    @Override
    public long countExecutionTraces(EventQuery query) {
        return chainEventMapper.countExecutionTraces(query);
    }

    @Override
    public ExecutionTrace getExecutionTrace(String executionId) {
        if (executionId == null || executionId.isEmpty()) return null;
        List<ChainEventPO> pos = chainEventMapper.selectByExecutionId(executionId);
        if (pos.isEmpty()) return null;

        List<ChainEvent> events = pos.stream()
                .map(JdbcEventQueryService::toDTO)
                .collect(Collectors.toList());

        ChainEventPO first = pos.get(0);
        long startTime = pos.stream().mapToLong(ChainEventPO::getTimestamp).min().orElse(0);
        long endTime = pos.stream().mapToLong(ChainEventPO::getTimestamp).max().orElse(0);

        // 计算状态和耗时
        Integer status = null;
        Long costMs = null;
        String errorMessage = null;
        int nodeCount = 0;
        int successCount = 0;
        int failedCount = 0;

        for (ChainEventPO po : pos) {
            String et = po.getEventType();
            if ("CHAIN_COMPLETED".equals(et)) { status = 1; costMs = po.getCostMs(); }
            else if ("CHAIN_FAILED".equals(et)) { status = 0; costMs = po.getCostMs(); errorMessage = po.getErrorMessage(); }
            else if ("CHAIN_TIMEOUT".equals(et)) { status = 0; costMs = po.getCostMs(); errorMessage = "执行超时"; }
            else if ("NODE_COMPLETED".equals(et) || "NODE_FALLBACK_SUCCESS".equals(et)) { successCount++; nodeCount++; }
            else if ("NODE_STARTED".equals(et)) { nodeCount++; }
            else if ("NODE_FAILED".equals(et) || "NODE_FALLBACK_FAILED".equals(et)) { failedCount++; nodeCount++; }
        }

        return ExecutionTrace.builder()
                .executionId(executionId)
                .chainName(first.getChainName())
                .executorId(first.getExecutorId())
                .appName(first.getAppName())
                .appCode(first.getAppCode())
                .startTime(startTime)
                .endTime(endTime)
                .costMs(costMs)
                .status(status)
                .eventCount(pos.size())
                .nodeCount(nodeCount)
                .successCount(successCount)
                .failedCount(failedCount)
                .errorMessage(errorMessage)
                .events(events)
                .build();
    }

    private LambdaQueryWrapper<ChainEventPO> buildQueryWrapper(EventQuery query) {
        LambdaQueryWrapper<ChainEventPO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(query.getExecutionId())) {
            wrapper.eq(ChainEventPO::getExecutionId, query.getExecutionId());
        }
        if (StringUtils.isNotBlank(query.getChainId())) {
            wrapper.eq(ChainEventPO::getChainId, query.getChainId());
        }
        if (StringUtils.isNotBlank(query.getExecutorId())) {
            wrapper.eq(ChainEventPO::getExecutorId, query.getExecutorId());
        }
        if (StringUtils.isNotBlank(query.getAppName())) {
            wrapper.eq(ChainEventPO::getAppName, query.getAppName());
        }
        if (query.getTenantId() != null) {
            wrapper.eq(ChainEventPO::getTenantId, query.getTenantId());
        }
        if (StringUtils.isNotBlank(query.getAppCode())) {
            wrapper.eq(ChainEventPO::getAppCode, query.getAppCode());
        }
        if (query.getEventTypes() != null && !query.getEventTypes().isEmpty()) {
            List<String> typeNames = query.getEventTypes().stream()
                    .map(Enum::name)
                    .collect(Collectors.toList());
            wrapper.in(ChainEventPO::getEventType, typeNames);
        }
        if (query.getStartTime() != null) {
            wrapper.ge(ChainEventPO::getTimestamp, query.getStartTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(ChainEventPO::getTimestamp, query.getEndTime());
        }
        if (query.getStatus() != null) {
            wrapper.eq(ChainEventPO::getStatus, query.getStatus());
        }
        if (StringUtils.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w
                    .like(ChainEventPO::getChainName, query.getKeyword())
                    .or()
                    .like(ChainEventPO::getNodeName, query.getKeyword())
            );
        }
        return wrapper;
    }

    /** PO → 轨迹摘要（不含 events 列表） */
    private static ExecutionTrace poToTraceSummary(ChainEventPO po) {
        return ExecutionTrace.builder()
                .executionId(po.getExecutionId())
                .chainName(po.getChainName())
                .executorId(po.getExecutorId())
                .appName(po.getAppName())
                .appCode(po.getAppCode())
                .startTime(po.getTimestamp())
                .status(po.getStatus())
                .eventCount(0)
                .build();
    }

    static ChainEvent toDTO(ChainEventPO po) {
        return ChainEvent.builder()
                .eventId(po.getEventId())
                .eventType(ChainEvent.EventType.valueOf(po.getEventType()))
                .executionId(po.getExecutionId())
                .chainId(po.getChainId())
                .chainName(po.getChainName())
                .nodeId(po.getNodeId())
                .nodeName(po.getNodeName())
                .executorId(po.getExecutorId())
                .appName(po.getAppName())
                .appCode(po.getAppCode())
                .tenantId(po.getTenantId())
                .params(po.getParams())
                .result(po.getResult())
                .errorMessage(po.getErrorMessage())
                .costMs(po.getCostMs())
                .status(po.getStatus())
                .timestamp(po.getTimestamp())
                .metadata(po.getMetadata())
                .build();
    }
}
