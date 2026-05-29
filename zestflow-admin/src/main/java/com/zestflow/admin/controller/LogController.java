package com.zestflow.admin.controller;

import com.zestflow.admin.client.dto.EventQueryDTO;
import com.zestflow.admin.service.LogService;
import com.zestflow.common.model.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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
        return Result.success(logService.queryEvents(query));
    }
}
