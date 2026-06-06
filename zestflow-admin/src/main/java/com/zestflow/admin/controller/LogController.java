package com.zestflow.admin.controller;

import com.zestflow.admin.client.CollectorQueryAggregator;
import com.zestflow.admin.client.CollectorQueryClient;
import com.zestflow.admin.model.vo.CollectorRegistryVO;
import com.zestflow.admin.service.CollectorRegistryService;
import com.zestflow.admin.util.SecurityUtils;
import com.zestflow.common.model.Result;
import com.zestflow.common.model.dto.ChainSnapshotDTO;
import com.zestflow.common.protocol.InvocationPayloadDTO;
import com.zestflow.common.protocol.NodeExecutionDetail;
import com.zestflow.common.protocol.EventQuery;
import com.zestflow.common.protocol.EventQueryResult;
import com.zestflow.common.protocol.ExecutionTrace;
import com.zestflow.common.protocol.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 日志查询控制器 — 多采集器 fan-out 聚合查询
 */
@Slf4j
@RestController
@RequestMapping("/logs")
@RequiredArgsConstructor
public class LogController {

    private final CollectorQueryClient collectorQueryClient;
    private final CollectorQueryAggregator collectorQueryAggregator;
    private final CollectorRegistryService collectorRegistryService;

    @Value("${zestflow.collector.api-url:}")
    private String collectorApiUrl;

    @Value("${zestflow.admin.protocol:http}")
    private String protocol;

    private final AtomicInteger collectorRoundRobin = new AtomicInteger(0);

    @PostMapping("/events/query")
    public Result<PageResult<EventQueryResult>> queryEvents(@RequestBody EventQuery query) {
        if (query.getTenantId() == null) {
            query.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        if (collectorRegistryService.listAllOnline().isEmpty() && isApiUrlBlank()) {
            return Result.fail(503, "COLLECTOR_UNAVAILABLE", "无可用采集器");
        }
        return Result.success(collectorQueryAggregator.queryEvents(query, query.getAppCode()));
    }

    @PostMapping("/executions")
    public Result<PageResult<ExecutionTrace>> queryExecutionTraces(@RequestBody EventQuery query) {
        if (query.getTenantId() == null) {
            query.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        if (collectorRegistryService.listAllOnline().isEmpty() && isApiUrlBlank()) {
            return Result.fail(503, "COLLECTOR_UNAVAILABLE", "无可用采集器");
        }
        return Result.success(collectorQueryAggregator.queryExecutionTraces(query, query.getAppCode()));
    }

    @GetMapping("/executions/{executionId}")
    public Result<ExecutionTrace> getExecutionTrace(@PathVariable String executionId) {
        ExecutionTrace trace = collectorQueryAggregator.getExecutionTrace(executionId, null);
        if (trace == null) {
            return Result.fail(404, "NOT_FOUND", "未找到执行轨迹");
        }
        return Result.success(trace);
    }

    @GetMapping("/executions/{executionId}/nodes/{nodeId}")
    public Result<NodeExecutionDetail> getNodeExecutionDetail(@PathVariable String executionId,
                                                               @PathVariable String nodeId,
                                                               @RequestParam(required = false) String nodeShape,
                                                               @RequestParam(required = false) String appCode) {
        NodeExecutionDetail detail = collectorQueryAggregator.getNodeExecutionDetail(
                executionId, nodeId, nodeShape, appCode);
        if (detail == null) {
            return Result.fail(404, "NOT_FOUND", "未找到节点执行详情");
        }
        return Result.success(detail);
    }

    @GetMapping("/snapshots")
    public Result<ChainSnapshotDTO> getSnapshot(@RequestParam String chainCode,
                                                 @RequestParam long timestamp) {
        String baseUrl = resolveCollectorBaseUrl(null);
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

    private boolean isApiUrlBlank() {
        return collectorApiUrl == null || collectorApiUrl.isEmpty();
    }

    private String resolveCollectorBaseUrl(String appCode) {
        if (appCode != null && !appCode.isBlank()) {
            List<CollectorRegistryVO> matched = collectorRegistryService.listOnlineByAppCode(appCode);
            if (!matched.isEmpty()) {
                CollectorRegistryVO c = matched.get(collectorRoundRobin.getAndIncrement() % matched.size());
                return protocol + "://" + c.getCollectorHost() + ":" + c.getCollectorPort();
            }
        }
        List<CollectorRegistryVO> collectors = collectorRegistryService.listAllOnline();
        if (!collectors.isEmpty()) {
            CollectorRegistryVO c = collectors.get(collectorRoundRobin.getAndIncrement() % collectors.size());
            return protocol + "://" + c.getCollectorHost() + ":" + c.getCollectorPort();
        }
        if (!isApiUrlBlank()) {
            log.info("注册表中无在线采集器，使用配置的 api-url={}", collectorApiUrl);
            return collectorApiUrl;
        }
        return null;
    }
}
