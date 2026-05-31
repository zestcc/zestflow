package com.zestflow.admin.service.impl;

import com.zestflow.admin.client.CollectorClient;
import com.zestflow.admin.client.dto.EventQueryDTO;
import com.zestflow.admin.client.dto.EventQueryResult;
import com.zestflow.admin.client.dto.ExecutionTraceResult;
import com.zestflow.admin.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 日志查询服务实现 — 委托 CollectorClient 从目标节点查询事件
 */
@Service
@RequiredArgsConstructor
public class LogServiceImpl implements LogService {

    private final CollectorClient collectorClient;

    @Override
    public EventQueryResult queryEvents(EventQueryDTO query) {
        return collectorClient.queryEvents(query);
    }

    @Override
    public EventQueryResult queryExecutionTraces(EventQueryDTO query) {
        return collectorClient.queryExecutionTraces(query);
    }

    @Override
    public ExecutionTraceResult getExecutionTrace(String executionId) {
        return collectorClient.getExecutionTrace(executionId);
    }
}
