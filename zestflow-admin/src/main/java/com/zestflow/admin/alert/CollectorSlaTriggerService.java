package com.zestflow.admin.alert;

import com.zestflow.admin.client.CollectorClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 触发 Collector 侧 SLA 扫描（手动 / 兼容入口）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectorSlaTriggerService {

    private final CollectorClient collectorClient;

    public String triggerScan() {
        String summary = collectorClient.triggerSlaScan();
        log.info("Collector SLA 手动扫描 {}", summary);
        return summary;
    }
}
