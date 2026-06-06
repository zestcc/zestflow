package com.zestflow.collector.spi;

import com.zestflow.common.protocol.EventQuery;
import com.zestflow.common.protocol.EventStats;
import com.zestflow.common.protocol.EventStatsQuery;
import com.zestflow.common.protocol.ExecutionRankItem;
import com.zestflow.common.protocol.ExecutionTrace;
import com.zestflow.common.protocol.ExecutionTrendPoint;
import com.zestflow.common.protocol.FailureClusterItem;
import com.zestflow.common.protocol.LogAnalyticsQuery;
import com.zestflow.common.protocol.NodeExecutionDetail;
import com.zestflow.common.model.dto.ChainEvent;

import java.util.List;

/**
 * 事件查询服务 SPI — Admin 通过此接口从 Collector 查询事件数据
 * <p>
 * 只读接口，不包含任何写方法。Admin 聚合展示数据，不直接管理数据。
 */
public interface EventQueryService {

    /**
     * 按条件查询事件列表（分页，轻量无 payload）
     */
    List<ChainEvent> queryEvents(EventQuery query);

    /**
     * 查询事件总数（用于分页）
     */
    long countEvents(EventQuery query);

    /**
     * 按事件 ID 查询单条事件详情（含 payload）
     */
    ChainEvent getById(String eventId);

    /**
     * 查询统计信息（按类型/状态/时间聚合）
     */
    EventStats queryStats(EventStatsQuery query);

    /**
     * 查询执行轨迹列表（按 executionId 分组，分页）
     */
    List<ExecutionTrace> queryExecutionTraces(EventQuery query);

    /**
     * 统计执行轨迹总数（用于分页）
     */
    long countExecutionTraces(EventQuery query);

    /**
     * 查询单次执行轨迹详情（含所有轻量事件，无 payload）
     */
    ExecutionTrace getExecutionTrace(String executionId);

    /**
     * 按需加载某次执行中单个节点的入参/出参详情
     *
     * @param nodeShape 图节点 shape（flow-start / flow-end / flow-task 等），可为空
     */
    NodeExecutionDetail getNodeExecutionDetail(String executionId, String nodeId, String nodeShape);

    /** 执行趋势（按 hour/day 桶） */
    List<ExecutionTrendPoint> queryExecutionTrend(LogAnalyticsQuery query);

    /** 链维度排行 */
    List<ExecutionRankItem> queryChainRanking(LogAnalyticsQuery query);

    /** 执行器维度排行 */
    List<ExecutionRankItem> queryExecutorRanking(LogAnalyticsQuery query);

    /** 节点维度排行（慢/失败热点） */
    List<ExecutionRankItem> queryNodeRanking(LogAnalyticsQuery query);

    /** 失败错误聚类 */
    List<FailureClusterItem> queryFailureClusters(LogAnalyticsQuery query);
}
