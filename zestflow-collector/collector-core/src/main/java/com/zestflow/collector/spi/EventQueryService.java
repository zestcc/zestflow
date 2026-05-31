package com.zestflow.collector.spi;

import com.zestflow.collector.model.dto.EventQuery;
import com.zestflow.collector.model.dto.EventStats;
import com.zestflow.collector.model.dto.EventStatsQuery;
import com.zestflow.collector.model.dto.ExecutionTrace;
import com.zestflow.common.model.dto.ChainEvent;

import java.util.List;

/**
 * 事件查询服务 SPI — Admin 通过此接口从 Collector 查询事件数据
 * <p>
 * 只读接口，不包含任何写方法。Admin 聚合展示数据，不直接管理数据。
 */
public interface EventQueryService {

    /**
     * 按条件查询事件列表（分页）
     */
    List<ChainEvent> queryEvents(EventQuery query);

    /**
     * 查询事件总数（用于分页）
     */
    long countEvents(EventQuery query);

    /**
     * 按事件 ID 查询单条事件详情
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
     * 查询单次执行轨迹详情（含所有事件）
     */
    ExecutionTrace getExecutionTrace(String executionId);
}
