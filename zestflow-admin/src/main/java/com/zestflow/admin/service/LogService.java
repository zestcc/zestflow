package com.zestflow.admin.service;

import com.zestflow.admin.client.dto.EventQueryDTO;
import com.zestflow.admin.client.dto.EventQueryResult;
import com.zestflow.admin.client.dto.ExecutionTraceResult;

/**
 * 日志查询服务
 */
public interface LogService {

    /**
     * 查询事件日志（分页）
     */
    EventQueryResult queryEvents(EventQueryDTO query);

    /**
     * 查询执行轨迹列表（按 executionId 分组）
     */
    EventQueryResult queryExecutionTraces(EventQueryDTO query);

    /**
     * 查询单次执行轨迹详情
     */
    ExecutionTraceResult getExecutionTrace(String executionId);
}
