package com.zestflow.collector.jdbc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zestflow.collector.jdbc.entity.ChainEventPO;
import com.zestflow.collector.jdbc.entity.ChainEventPayloadPO;
import com.zestflow.collector.jdbc.mapper.ChainEventMapper;
import com.zestflow.collector.jdbc.mapper.ChainEventPayloadMapper;
import com.zestflow.common.protocol.EventQuery;
import com.zestflow.common.protocol.EventStats;
import com.zestflow.common.protocol.EventStatsQuery;
import com.zestflow.common.protocol.ExecutionTrace;
import com.zestflow.common.protocol.NodeExecutionDetail;
import com.zestflow.collector.spi.EventQueryService;
import com.zestflow.common.model.dto.ChainEvent;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JDBC 事件查询服务 — 索引轻量查询 + 载荷按需加载
 */
@RequiredArgsConstructor
public class JdbcEventQueryService implements EventQueryService {

    private final ChainEventMapper chainEventMapper;
    private final ChainEventPayloadMapper chainEventPayloadMapper;

    @Override
    public List<ChainEvent> queryEvents(EventQuery query) {
        IPage<ChainEventPO> page = new Page<>(query.getPage(), query.getPageSize());
        LambdaQueryWrapper<ChainEventPO> wrapper = buildQueryWrapper(query);
        wrapper.orderByDesc(ChainEventPO::getTimestamp);
        IPage<ChainEventPO> result = chainEventMapper.selectPage(page, wrapper);
        return result.getRecords().stream()
                .map(JdbcEventQueryService::toSlimDTO)
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
        if (po == null) {
            return null;
        }
        ChainEvent dto = toSlimDTO(po);
        mergePayload(dto, chainEventPayloadMapper.selectByEventId(eventId));
        return dto;
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
        if (executionId == null || executionId.isEmpty()) {
            return null;
        }
        List<ChainEventPO> pos = chainEventMapper.selectByExecutionId(executionId);
        if (pos.isEmpty()) {
            return null;
        }

        List<ChainEvent> events = pos.stream()
                .map(JdbcEventQueryService::toSlimDTO)
                .collect(Collectors.toList());

        ChainEventPO first = pos.get(0);
        long startTime = pos.stream().mapToLong(ChainEventPO::getTimestamp).min().orElse(0);
        long endTime = pos.stream().mapToLong(ChainEventPO::getTimestamp).max().orElse(0);

        Integer status = null;
        Long costMs = null;
        String errorMessage = null;
        NodeMetrics nodeMetrics = aggregateNodeMetrics(pos);

        for (ChainEventPO po : pos) {
            String et = po.getEventType();
            if ("CHAIN_COMPLETED".equals(et)) {
                status = 1;
                costMs = po.getCostMs();
            } else if ("CHAIN_FAILED".equals(et)) {
                status = 0;
                costMs = po.getCostMs();
                errorMessage = loadErrorMessage(po.getEventId());
            } else if ("CHAIN_TIMEOUT".equals(et)) {
                status = 0;
                costMs = po.getCostMs();
                errorMessage = "执行超时";
            }
        }
        if (status == null) {
            status = -1;
        }

        return ExecutionTrace.builder()
                .executionId(executionId)
                .chainCode(first.getChainId())
                .chainName(first.getChainName())
                .executorId(first.getExecutorId())
                .appName(first.getAppName())
                .appCode(first.getAppCode())
                .startTime(startTime)
                .endTime(endTime)
                .costMs(costMs)
                .status(status)
                .eventCount(pos.size())
                .nodeCount(nodeMetrics.nodeCount())
                .successCount(nodeMetrics.successCount())
                .failedCount(nodeMetrics.failedCount())
                .errorMessage(errorMessage)
                .events(events)
                .build();
    }

    @Override
    public NodeExecutionDetail getNodeExecutionDetail(String executionId, String nodeId, String nodeShape) {
        if (executionId == null || executionId.isBlank()) {
            return null;
        }
        List<ChainEventPO> pos = chainEventMapper.selectByExecutionId(executionId);
        if (pos.isEmpty()) {
            return null;
        }

        List<ChainEvent> timeline = new ArrayList<>();
        String params = null;
        String result = null;
        String errorMessage = null;
        Long costMs = null;
        Integer status = -1;
        String nodeName = null;

        if ("flow-start".equals(nodeShape)) {
            for (ChainEventPO po : pos) {
                if ("CHAIN_STARTED".equals(po.getEventType())) {
                    ChainEventPayloadPO payload = chainEventPayloadMapper.selectByEventId(po.getEventId());
                    params = payload != null ? payload.getParams() : null;
                    nodeName = po.getChainName();
                    timeline.add(toSlimDTO(po));
                    status = 1;
                    break;
                }
            }
        } else if ("flow-end".equals(nodeShape)) {
            ChainEventPO terminal = findTerminalChainEvent(pos);
            if (terminal != null) {
                ChainEventPayloadPO payload = chainEventPayloadMapper.selectByEventId(terminal.getEventId());
                result = payload != null ? payload.getResult() : null;
                errorMessage = payload != null ? payload.getErrorMessage() : null;
                costMs = terminal.getCostMs();
                nodeName = terminal.getChainName();
                status = terminalStatus(terminal.getEventType());
                timeline.add(toSlimDTO(terminal));
            }
        } else if (nodeId != null && !nodeId.isBlank()) {
            for (ChainEventPO po : pos) {
                if (!nodeId.equals(po.getNodeId())) {
                    continue;
                }
                String et = po.getEventType();
                if (et != null && et.startsWith("NODE_")) {
                    timeline.add(toSlimDTO(po));
                }
                if (nodeName == null && po.getNodeName() != null) {
                    nodeName = po.getNodeName();
                }
            }
            params = loadPayloadField(pos, nodeId, "NODE_STARTED", true);
            result = loadPayloadField(pos, nodeId, "NODE_COMPLETED", false);
            if (result == null) {
                result = loadPayloadField(pos, nodeId, "NODE_FALLBACK_SUCCESS", false);
            }
            errorMessage = loadPayloadField(pos, nodeId, "NODE_FAILED", false);
            if (errorMessage == null) {
                ChainEventPayloadPO failPayload = findPayload(pos, nodeId, "NODE_FAILED");
                if (failPayload != null) {
                    errorMessage = failPayload.getErrorMessage();
                }
            }
            costMs = findNodeCostMs(pos, nodeId);
            status = resolveNodeStatus(pos, nodeId);
        }

        return NodeExecutionDetail.builder()
                .executionId(executionId)
                .nodeId(nodeId)
                .nodeName(nodeName)
                .nodeShape(nodeShape)
                .params(params)
                .result(result)
                .errorMessage(errorMessage)
                .costMs(costMs)
                .status(status)
                .timeline(timeline.isEmpty() ? null : timeline)
                .build();
    }

    private String loadErrorMessage(String eventId) {
        ChainEventPayloadPO payload = chainEventPayloadMapper.selectByEventId(eventId);
        return payload != null ? payload.getErrorMessage() : null;
    }

    private static ChainEventPO findTerminalChainEvent(List<ChainEventPO> pos) {
        ChainEventPO completed = null;
        ChainEventPO failed = null;
        for (ChainEventPO po : pos) {
            if ("CHAIN_COMPLETED".equals(po.getEventType())) {
                completed = po;
            } else if ("CHAIN_FAILED".equals(po.getEventType()) || "CHAIN_TIMEOUT".equals(po.getEventType())) {
                failed = po;
            }
        }
        return completed != null ? completed : failed;
    }

    private static int terminalStatus(String eventType) {
        return "CHAIN_COMPLETED".equals(eventType) ? 1 : 0;
    }

    private String loadPayloadField(List<ChainEventPO> pos, String nodeId, String eventType, boolean paramsField) {
        ChainEventPayloadPO payload = findPayload(pos, nodeId, eventType);
        if (payload == null) {
            return null;
        }
        return paramsField ? payload.getParams() : payload.getResult();
    }

    private ChainEventPayloadPO findPayload(List<ChainEventPO> pos, String nodeId, String eventType) {
        for (int i = pos.size() - 1; i >= 0; i--) {
            ChainEventPO po = pos.get(i);
            if (eventType.equals(po.getEventType()) && nodeId.equals(po.getNodeId())) {
                return chainEventPayloadMapper.selectByEventId(po.getEventId());
            }
        }
        return null;
    }

    private static Long findNodeCostMs(List<ChainEventPO> pos, String nodeId) {
        for (int i = pos.size() - 1; i >= 0; i--) {
            ChainEventPO po = pos.get(i);
            if (nodeId.equals(po.getNodeId()) && po.getCostMs() != null) {
                String et = po.getEventType();
                if ("NODE_COMPLETED".equals(et) || "NODE_FAILED".equals(et)
                        || "NODE_FALLBACK_SUCCESS".equals(et) || "NODE_FALLBACK_FAILED".equals(et)) {
                    return po.getCostMs();
                }
            }
        }
        return null;
    }

    private static int resolveNodeStatus(List<ChainEventPO> pos, String nodeId) {
        boolean started = false;
        for (ChainEventPO po : pos) {
            if (!nodeId.equals(po.getNodeId())) {
                continue;
            }
            String et = po.getEventType();
            if ("NODE_COMPLETED".equals(et) || "NODE_FALLBACK_SUCCESS".equals(et)) {
                return 1;
            }
            if ("NODE_FAILED".equals(et) || "NODE_FALLBACK_FAILED".equals(et)) {
                return 0;
            }
            if ("NODE_STARTED".equals(et)) {
                started = true;
            }
        }
        return started ? -1 : -1;
    }

    static NodeMetrics aggregateNodeMetrics(List<ChainEventPO> events) {
        Set<String> nodeIds = new HashSet<>();
        Set<String> successNodeIds = new HashSet<>();
        Set<String> failedNodeIds = new HashSet<>();
        for (ChainEventPO po : events) {
            String et = po.getEventType();
            if (et == null) {
                continue;
            }
            String nodeId = po.getNodeId();
            boolean hasNodeId = nodeId != null && !nodeId.isEmpty();
            if (et.startsWith("NODE_") && hasNodeId) {
                nodeIds.add(nodeId);
            }
            if (hasNodeId && ("NODE_COMPLETED".equals(et) || "NODE_FALLBACK_SUCCESS".equals(et))) {
                successNodeIds.add(nodeId);
            } else if (hasNodeId && ("NODE_FAILED".equals(et) || "NODE_FALLBACK_FAILED".equals(et))) {
                failedNodeIds.add(nodeId);
            }
        }
        return new NodeMetrics(nodeIds.size(), successNodeIds.size(), failedNodeIds.size());
    }

    record NodeMetrics(int nodeCount, int successCount, int failedCount) {}

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

    private static ExecutionTrace poToTraceSummary(ChainEventPO po) {
        int eventCount = po.getEventCount() != null ? po.getEventCount() : 0;
        int nodeCount = po.getNodeCount() != null ? po.getNodeCount() : 0;
        int successCount = po.getSuccessCount() != null ? po.getSuccessCount() : 0;
        int failedCount = po.getFailedCount() != null ? po.getFailedCount() : 0;
        Integer status = po.getStatus();
        if (status == null) {
            status = -1;
        }
        return ExecutionTrace.builder()
                .executionId(po.getExecutionId())
                .chainCode(po.getChainId())
                .chainName(po.getChainName())
                .executorId(po.getExecutorId())
                .appName(po.getAppName())
                .appCode(po.getAppCode())
                .startTime(po.getTimestamp() != null ? po.getTimestamp() : 0L)
                .costMs(po.getCostMs())
                .status(status)
                .eventCount(eventCount)
                .nodeCount(nodeCount)
                .successCount(successCount)
                .failedCount(failedCount)
                .build();
    }

    static ChainEvent toSlimDTO(ChainEventPO po) {
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
                .costMs(po.getCostMs())
                .status(po.getStatus())
                .timestamp(po.getTimestamp())
                .metadata(po.getMetadata())
                .build();
    }

    private static void mergePayload(ChainEvent dto, ChainEventPayloadPO payload) {
        if (dto == null || payload == null) {
            return;
        }
        dto.setParams(payload.getParams());
        dto.setResult(payload.getResult());
        dto.setErrorMessage(payload.getErrorMessage());
    }
}
