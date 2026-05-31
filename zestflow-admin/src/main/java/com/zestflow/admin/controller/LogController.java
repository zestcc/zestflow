package com.zestflow.admin.controller;

import com.zestflow.admin.model.vo.CollectorRegistryVO;
import com.zestflow.admin.service.CollectorRegistryService;
import com.zestflow.collector.client.CollectorQueryClient;
import com.zestflow.collector.model.dto.EventQuery;
import com.zestflow.common.model.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 日志查询控制器 — 直连采集器 REST API 查询事件/轨迹
 */
@Slf4j
@RestController
@RequestMapping("/logs")
@RequiredArgsConstructor
public class LogController {

    private final CollectorQueryClient collectorQueryClient;
    private final CollectorRegistryService collectorRegistryService;

    /**
     * 查询事件日志（分页）
     */
    @PostMapping("/events/query")
    public Result<CollectorQueryClient.PageResult<CollectorQueryClient.EventQueryResult>> queryEvents(
            @RequestBody EventQuery query) {
        String baseUrl = resolveCollectorBaseUrl();
        if (baseUrl == null) {
            return Result.success(new CollectorQueryClient.PageResult<>(List.of(), 0L, query.getPage(), query.getPageSize()));
        }
        var result = collectorQueryClient.queryEvents(baseUrl, query);
        return Result.success(result);
    }

    /**
     * 查询执行轨迹列表（分页）
     */
    @PostMapping("/executions")
    public Result<CollectorQueryClient.PageResult<com.zestflow.collector.model.dto.ExecutionTrace>> queryExecutionTraces(
            @RequestBody EventQuery query) {
        String baseUrl = resolveCollectorBaseUrl();
        if (baseUrl == null) {
            return Result.success(new CollectorQueryClient.PageResult<>(List.of(), 0L, query.getPage(), query.getPageSize()));
        }
        var result = collectorQueryClient.queryExecutionTraces(baseUrl, query);
        return Result.success(result);
    }

    /**
     * 查询单次执行轨迹详情
     */
    @GetMapping("/executions/{executionId}")
    public Result<com.zestflow.collector.model.dto.ExecutionTrace> getExecutionTrace(
            @PathVariable String executionId) {
        String baseUrl = resolveCollectorBaseUrl();
        if (baseUrl == null) {
            return Result.success(null);
        }
        var result = collectorQueryClient.getExecutionTrace(baseUrl, executionId);
        return Result.success(result);
    }

    /**
     * 从采集器注册表中查找第一个在线采集器地址
     */
    private String resolveCollectorBaseUrl() {
        List<CollectorRegistryVO> collectors = collectorRegistryService.listAllOnline();
        if (collectors.isEmpty()) {
            log.warn("无在线采集器可用，日志查询返回空");
            return null;
        }
        CollectorRegistryVO c = collectors.get(0);
        return "http://" + c.getCollectorHost() + ":" + c.getCollectorPort();
    }
}
