package com.zestflow.admin.controller;

import com.zestflow.admin.client.CollectorQueryClient;
import com.zestflow.admin.model.vo.CollectorRegistryVO;
import com.zestflow.admin.service.CollectorRegistryService;
import com.zestflow.admin.util.SecurityUtils;
import com.zestflow.common.model.Result;
import com.zestflow.common.model.dto.ChainSnapshotDTO;
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
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final CollectorQueryClient collectorQueryClient;
    private final CollectorRegistryService collectorRegistryService;

    @Value("${zestflow.collector.api-url:}")
    private String collectorApiUrl;

    /** 服务间通信协议（http/https） */
    @Value("${zestflow.admin.protocol:http}")
    private String protocol;

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
     * 查询指定时刻的图数据快照
     */
    @GetMapping("/snapshots")
    public Result<ChainSnapshotDTO> getSnapshot(@RequestParam String chainCode,
                                                 @RequestParam long timestamp) {
        String baseUrl = resolveCollectorBaseUrl();
        if (baseUrl == null) {
            return Result.fail(503, "COLLECTOR_UNAVAILABLE", "无可用采集器");
        }
        ChainSnapshotDTO snapshot = collectorQueryClient.getSnapshot(baseUrl, chainCode, timestamp,
                SecurityUtils.getCurrentTenantId());
        if (snapshot == null) {
            return Result.fail(404, "NOT_FOUND", "未找到图数据快照");
        }
        return Result.success(snapshot);
    }

    /**
     * 解析采集器地址
     * <p>
     * 优先级：注册表中第一个在线采集器 > 配置的 api-url（可选兜底）
     * 采集器启动时自动注册到 Admin，上报 host:port，Admin 据此直连。
     * api-url 仅在无在线采集器时作为兜底，用于特殊网络拓扑。
     */
    private String resolveCollectorBaseUrl() {
        return resolveCollectorBaseUrl(null);
    }

    private String resolveCollectorBaseUrl(String appCode) {
        // 1. 按 appCode 查找精度最高
        if (appCode != null) {
            List<CollectorRegistryVO> matched = collectorRegistryService.listOnlineByAppCode(appCode);
            if (!matched.isEmpty()) {
                return protocol + "://" + matched.get(0).getCollectorHost() + ":" + matched.get(0).getCollectorPort();
            }
        }
        // 2. 任意在线采集器
        List<CollectorRegistryVO> collectors = collectorRegistryService.listAllOnline();
        if (!collectors.isEmpty()) {
            CollectorRegistryVO c = collectors.get(0);
            return protocol + "://" + c.getCollectorHost() + ":" + c.getCollectorPort();
        }
        // 3. 配置兜底
        if (collectorApiUrl != null && !collectorApiUrl.isEmpty()) {
            log.info("注册表中无在线采集器，使用配置的 api-url={}", collectorApiUrl);
            return collectorApiUrl;
        }
        log.warn("无在线采集器可用且未配置 zestflow.collector.api-url，日志查询返回空");
        return null;
    }
}
