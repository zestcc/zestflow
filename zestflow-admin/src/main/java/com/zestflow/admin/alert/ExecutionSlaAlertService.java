package com.zestflow.admin.alert;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @deprecated 扫描已下沉 Collector；保留 Bean 供历史测试与手动触发兼容。
 */
@Deprecated
@Service
@RequiredArgsConstructor
public class ExecutionSlaAlertService {

    private final CollectorSlaTriggerService collectorSlaTriggerService;

    public String scan() {
        return collectorSlaTriggerService.triggerScan();
    }
}
