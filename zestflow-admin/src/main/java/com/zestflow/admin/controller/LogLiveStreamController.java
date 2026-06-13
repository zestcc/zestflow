package com.zestflow.admin.controller;

import com.zestflow.admin.service.log.ExecutionLiveStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 日志执行轨迹 SSE — 详情抽屉内实时刷新节点着色。
 */
@RestController
@RequestMapping("/logs/executions")
@RequiredArgsConstructor
public class LogLiveStreamController {

    private final ExecutionLiveStreamService executionLiveStreamService;

    @GetMapping(value = "/{executionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamExecution(@PathVariable String executionId,
                                      @RequestParam(required = false) String appCode) {
        return executionLiveStreamService.stream(executionId, appCode);
    }
}
