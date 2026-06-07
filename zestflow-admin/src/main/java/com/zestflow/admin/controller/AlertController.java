package com.zestflow.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zestflow.admin.alert.AlertConfigService;
import com.zestflow.admin.alert.AlertHistoryService;
import com.zestflow.admin.alert.CollectorSlaTriggerService;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.model.dto.AlertConfigSaveDTO;
import com.zestflow.admin.model.vo.AlertConfigVO;
import com.zestflow.admin.model.vo.AlertHistoryVO;
import com.zestflow.admin.model.vo.AlertScanResultVO;
import com.zestflow.admin.model.dto.AlertConfigSaveDTO;
import com.zestflow.admin.model.vo.AlertConfigVO;
import com.zestflow.admin.model.vo.AlertHistoryVO;
import com.zestflow.admin.model.vo.AlertScanResultVO;
import com.zestflow.admin.service.TenantAppContext;
import com.zestflow.common.exception.BizException;
import com.zestflow.common.model.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Set;

@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertConfigService alertConfigService;
    private final AlertHistoryService alertHistoryService;
    private final TenantAppContext tenantAppContext;
    private final CollectorSlaTriggerService collectorSlaTriggerService;

    @GetMapping("/config")
    public Result<AlertConfigVO> getConfig() {
        requireAuthenticated();
        Long tenantId = tenantAppContext.getCurrentTenantId();
        return Result.success(alertConfigService.getConfig(tenantId));
    }

    @PutMapping("/config")
    public Result<AlertConfigVO> saveConfig(@RequestBody AlertConfigSaveDTO dto) {
        requireAuthenticated();
        Long tenantId = tenantAppContext.getCurrentTenantId();
        return Result.success(alertConfigService.saveConfig(tenantId, dto));
    }

    @DeleteMapping("/config")
    public Result<AlertConfigVO> resetConfig() {
        requireAuthenticated();
        Long tenantId = tenantAppContext.getCurrentTenantId();
        return Result.success(alertConfigService.resetConfig(tenantId));
    }

    /** 立即执行 SLA 告警扫描（写入 schedule_log） */
    @PostMapping("/scan")
    public Result<AlertScanResultVO> scanNow() {
        requireAuthenticated();
        long start = System.currentTimeMillis();
        try {
            String summary = collectorSlaTriggerService.triggerScan();
            boolean ok = summary != null && !summary.contains("error=");
            return Result.success(AlertScanResultVO.builder()
                    .success(ok)
                    .summary(summary)
                    .errorMessage(ok ? null : summary)
                    .costMs(System.currentTimeMillis() - start)
                    .build());
        } catch (Exception e) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, e.getMessage());
        }
    }

    @GetMapping("/history")
    public Result<IPage<AlertHistoryVO>> listHistory(
            @RequestParam(required = false) String appCode,
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        requireAuthenticated();
        Long tenantId = tenantAppContext.getCurrentTenantId();
        Set<String> appFilter = resolveAppFilter(appCode);
        return Result.success(alertHistoryService.list(
                tenantId, appCode, ruleCode, startTime, endTime, page, size, appFilter));
    }

    /** 非超管只能查看已分配模块的历史 */
    private Set<String> resolveAppFilter(String requestedAppCode) {
        Set<String> userApps = tenantAppContext.getCurrentUserAppCodes();
        if (userApps.isEmpty()) {
            return null;
        }
        if (requestedAppCode != null && !requestedAppCode.isBlank()) {
            if (!userApps.contains(requestedAppCode)) {
                throw new BizException(ErrorCode.PERMISSION_DENIED);
            }
            return null;
        }
        return userApps;
    }

    private void requireAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }
}
