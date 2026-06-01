package com.zestflow.admin.controller;

import com.zestflow.admin.client.CollectorQueryClient;
import com.zestflow.admin.model.vo.CollectorRegistryVO;
import com.zestflow.admin.service.CollectorRegistryService;
import com.zestflow.admin.util.SecurityUtils;
import com.zestflow.common.model.Result;
import com.zestflow.common.protocol.EventQuery;
import com.zestflow.common.protocol.EventQueryResult;
import com.zestflow.common.protocol.ExecutionTrace;
import com.zestflow.common.protocol.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${zestflow.collector.api-url:}")
    private String collectorApiUrl;

    /**
     * 查询事件日志（分页）
     */
    @PostMapping("/events/query")
    public Result<PageResult<EventQueryResult>> queryEvents(
            @RequestBody EventQuery query) {
        // 注入当前租户 ID，保证查询隔离
        if (query.getTenantId() == null) {
            query.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        String baseUrl = resolveCollectorBaseUrl();
        if (baseUrl == null) {
            return Result.success(new PageResult<>(List.of(), 0L, query.getPage(), query.getPageSize()));
        }
        var result = collectorQueryClient.queryEvents(baseUrl, query);
        return Result.success(result);
    }

    /**
     * 查询执行轨迹列表（分页）
     */
    @PostMapping("/executions")
    public Result<PageResult<ExecutionTrace>> queryExecutionTraces(
            @RequestBody EventQuery query) {
        // 注入当前租户 ID
        if (query.getTenantId() == null) {
            query.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        String baseUrl = resolveCollectorBaseUrl();
        if (baseUrl == null) {
            return Result.success(new PageResult<>(List.of(), 0L, query.getPage(), query.getPageSize()));
        }
        var result = collectorQueryClient.queryExecutionTraces(baseUrl, query);
        return Result.success(result);
    }

    /**
     * 查询单次执行轨迹详情
     */
    @GetMapping("/executions/{executionId}")
    public Result<ExecutionTrace> getExecutionTrace(
            @PathVariable String executionId) {
        String baseUrl = resolveCollectorBaseUrl();
        if (baseUrl == null) {
            return Result.success(null);
        }
        var result = collectorQueryClient.getExecutionTrace(baseUrl, executionId);
        return Result.success(result);
    }

    /**
     * 解析采集器地址
     * <p>
     * 优先级：配置的 api-url > 注册表中第一个在线采集器
     * 配置的 api-url 优先，因为嵌入式模式下采集器注册端口（9998）与 HTTP 服务端口（8081）不一致，
     * 导致 registry 地址不可用。已配置 api-url 说明运维指定了正确的采集器端点。
     */
    private String resolveCollectorBaseUrl() {
        if (collectorApiUrl != null && !collectorApiUrl.isEmpty()) {
            return collectorApiUrl;
        }
        List<CollectorRegistryVO> collectors = collectorRegistryService.listAllOnline();
        if (!collectors.isEmpty()) {
            CollectorRegistryVO c = collectors.get(0);
            return "http://" + c.getCollectorHost() + ":" + c.getCollectorPort();
        }
        log.warn("无在线采集器可用且未配置 zestflow.collector.api-url，日志查询返回空");
        return null;
    }
}
