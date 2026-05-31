package com.zestflow.collector.jdbc.controller;

import com.zestflow.collector.jdbc.config.CollectorProperties;
import com.zestflow.collector.model.dto.EventQuery;
import com.zestflow.collector.model.dto.EventStats;
import com.zestflow.collector.model.dto.EventStatsQuery;
import com.zestflow.collector.model.dto.ExecutionTrace;
import com.zestflow.collector.spi.EventQueryService;
import com.zestflow.common.model.Result;
import com.zestflow.common.model.dto.ChainEvent;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Collector REST 控制器 — Admin 通过此接口查询事件数据
 * <p>
 * 只读接口，仅提供 GET 查询。Admin 聚合展示数据，不直接管理数据。
 * 安全：通过 X-Collector-Token 请求头认证。
 */
@Slf4j
@RestController
@RequestMapping("/collector")
@RequiredArgsConstructor
public class CollectorController {

    private final EventQueryService eventQueryService;
    private final CollectorProperties properties;

    /**
     * 查询事件列表（分页）
     */
    @PostMapping("/events/query")
    public Result<?> queryEvents(@RequestBody EventQuery query,
                                  HttpServletRequest request) {
        if (!checkToken(request)) {
            return Result.fail(401, "UNAUTHORIZED", "Invalid collector token");
        }
        List<ChainEvent> list = eventQueryService.queryEvents(query);
        long total = eventQueryService.countEvents(query);
        return Result.success(new PageResult<>(list, total, query.getPage(), query.getPageSize()));
    }

    /**
     * 查询单条事件详情
     */
    @GetMapping("/events/{eventId}")
    public Result<?> getEvent(@PathVariable String eventId,
                               HttpServletRequest request) {
        if (!checkToken(request)) {
            return Result.fail(401, "UNAUTHORIZED", "Invalid collector token");
        }
        ChainEvent event = eventQueryService.getById(eventId);
        if (event == null) {
            return Result.fail(404, "NOT_FOUND", "Event not found");
        }
        return Result.success(event);
    }

    /**
     * 查询事件统计
     */
    @PostMapping("/events/stats")
    public Result<?> queryStats(@RequestBody EventStatsQuery query,
                                 HttpServletRequest request) {
        if (!checkToken(request)) {
            return Result.fail(401, "UNAUTHORIZED", "Invalid collector token");
        }
        EventStats stats = eventQueryService.queryStats(query);
        return Result.success(stats);
    }

    /**
     * 查询执行轨迹列表（按 executionId 分组）
     */
    @PostMapping("/events/executions")
    public Result<?> queryExecutionTraces(@RequestBody EventQuery query,
                                           HttpServletRequest request) {
        if (!checkToken(request)) {
            return Result.fail(401, "UNAUTHORIZED", "Invalid collector token");
        }
        List<ExecutionTrace> list = eventQueryService.queryExecutionTraces(query);
        long total = eventQueryService.countExecutionTraces(query);
        return Result.success(new PageResult<>(list, total, query.getPage(), query.getPageSize()));
    }

    /**
     * 查询单次执行轨迹详情（含所有事件 + 摘要）
     */
    @GetMapping("/events/executions/{executionId}")
    public Result<?> getExecutionTrace(@PathVariable String executionId,
                                        HttpServletRequest request) {
        if (!checkToken(request)) {
            return Result.fail(401, "UNAUTHORIZED", "Invalid collector token");
        }
        ExecutionTrace trace = eventQueryService.getExecutionTrace(executionId);
        if (trace == null) {
            return Result.fail(404, "NOT_FOUND", "Execution trace not found");
        }
        return Result.success(trace);
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Result<?> health() {
        return Result.success();
    }

    private boolean checkToken(HttpServletRequest request) {
        String token = properties.getAccessToken();
        if (token == null || token.isEmpty()) {
            return true; // 未配置 token 则不校验
        }
        String header = request.getHeader("X-Collector-Token");
        return token.equals(header);
    }

    /** 分页结果包装 */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class PageResult<T> {
        private List<T> list;
        private long total;
        private int page;
        private int pageSize;
    }
}
