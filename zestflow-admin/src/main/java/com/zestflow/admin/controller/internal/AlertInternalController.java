package com.zestflow.admin.controller.internal;

import com.zestflow.admin.alert.AlertOrchestrationService;
import com.zestflow.common.model.Result;
import com.zestflow.common.protocol.SlaAlertMetricsReportDTO;
import com.zestflow.common.protocol.SlaAlertScopeDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SLA 内部 API — Collector 拉取扫描范围并上报 EventStats（registry-token 鉴权）。
 */
@RestController
@RequestMapping("/internal/alerts")
@RequiredArgsConstructor
public class AlertInternalController {

    private final AlertOrchestrationService alertOrchestrationService;

    @GetMapping("/scopes")
    public Result<List<SlaAlertScopeDTO>> listScopes() {
        return Result.success(alertOrchestrationService.listEnabledScopes());
    }

    @PostMapping("/process-metrics")
    public Result<Map<String, Object>> processMetrics(@RequestBody SlaAlertMetricsReportDTO report) {
        int[] sent = alertOrchestrationService.processMetricsReport(report);
        Map<String, Object> body = new HashMap<>();
        body.put("alerts", sent[0]);
        body.put("emails", sent[1]);
        return Result.success(body);
    }
}
