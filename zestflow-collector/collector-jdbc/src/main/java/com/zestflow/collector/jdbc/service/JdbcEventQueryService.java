package com.zestflow.collector.jdbc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zestflow.collector.jdbc.entity.ChainEventPO;
import com.zestflow.collector.jdbc.mapper.ChainEventMapper;
import com.zestflow.collector.model.dto.EventQuery;
import com.zestflow.collector.model.dto.EventStats;
import com.zestflow.collector.model.dto.EventStatsQuery;
import com.zestflow.collector.spi.EventQueryService;
import com.zestflow.common.model.dto.ChainEvent;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.List;
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
        // 使用 Mapper 自定义统计查询，此处返回基础统计
        LambdaQueryWrapper<ChainEventPO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(query.getAppName())) {
            wrapper.eq(ChainEventPO::getAppName, query.getAppName());
        }
        if (StringUtils.isNotBlank(query.getExecutorId())) {
            wrapper.eq(ChainEventPO::getExecutorId, query.getExecutorId());
        }
        if (StringUtils.isNotBlank(query.getChainId())) {
            wrapper.eq(ChainEventPO::getChainId, query.getChainId());
        }
        if (query.getStartTime() != null) {
            wrapper.ge(ChainEventPO::getTimestamp, query.getStartTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(ChainEventPO::getTimestamp, query.getEndTime());
        }

        long totalCount = chainEventMapper.selectCount(wrapper);
        // 需要额外 SQL 统计，此处简化
        return EventStats.builder()
                .totalCount(totalCount)
                .build();
    }

    private LambdaQueryWrapper<ChainEventPO> buildQueryWrapper(EventQuery query) {
        LambdaQueryWrapper<ChainEventPO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(query.getChainId())) {
            wrapper.eq(ChainEventPO::getChainId, query.getChainId());
        }
        if (StringUtils.isNotBlank(query.getExecutorId())) {
            wrapper.eq(ChainEventPO::getExecutorId, query.getExecutorId());
        }
        if (StringUtils.isNotBlank(query.getAppName())) {
            wrapper.eq(ChainEventPO::getAppName, query.getAppName());
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

    static ChainEvent toDTO(ChainEventPO po) {
        return ChainEvent.builder()
                .eventId(po.getEventId())
                .eventType(ChainEvent.EventType.valueOf(po.getEventType()))
                .chainId(po.getChainId())
                .chainName(po.getChainName())
                .nodeId(po.getNodeId())
                .nodeName(po.getNodeName())
                .executorId(po.getExecutorId())
                .appName(po.getAppName())
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
