package com.zestflow.admin.controller;

import com.zestflow.admin.client.dto.EventQueryDTO;
import com.zestflow.admin.client.dto.EventQueryResult;
import com.zestflow.admin.client.dto.ExecutionTraceResult;
import com.zestflow.admin.service.LogService;
import com.zestflow.common.model.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 日志查询控制器 — 为 Admin UI 提供日志查询接口
 */
@Slf4j
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    /**
     * 查询事件日志（分页）
     */
    @PostMapping("/events/query")
    public Result<?> queryEvents(@RequestBody EventQueryDTO query) {
        EventQueryResult result = logService.queryEvents(query);
        if (result.getData() != null) {
            return Result.success(result.getData());
        }
        return Result.success(emptyPage(query.getPage(), query.getPageSize()));
    }

    /**
     * 查询执行轨迹列表（按 executionId 分组，用于 Admin 执行记录页面）
     */
    @PostMapping("/executions")
    public Result<?> queryExecutionTraces(@RequestBody EventQueryDTO query) {
        EventQueryResult result = logService.queryExecutionTraces(query);
        if (result.getData() != null) {
            return Result.success(result.getData());
        }
        return Result.success(emptyPage(query.getPage(), query.getPageSize()));
    }

    /**
     * 查询单次执行轨迹详情（含所有事件 + 摘要 + 可用于流程图渲染的数据）
     */
    @GetMapping("/executions/{executionId}")
    public Result<?> getExecutionTrace(@PathVariable String executionId) {
        ExecutionTraceResult result = logService.getExecutionTrace(executionId);
        if (result.getData() != null) {
            return Result.success(result.getData());
        }
        return Result.fail(404, "NOT_FOUND", "执行记录不存在");
    }

    private Map<String, Object> emptyPage(int page, int pageSize) {
        Map<String, Object> pageData = new LinkedHashMap<>();
        pageData.put("list", Collections.emptyList());
        pageData.put("total", 0);
        pageData.put("page", page);
        pageData.put("pageSize", pageSize);
        return pageData;
    }
}
