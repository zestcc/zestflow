package com.zestflow.collector.jdbc.alert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Collector 本地 SLA 扫描定时器（默认 5 分钟）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "zestflow.collector.alert", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CollectorSlaAlertMonitor {

    private final CollectorSlaAlertService slaAlertService;

    @Scheduled(fixedRateString = "${zestflow.collector.alert.scan-interval-ms:300000}")
    public void scan() {
        try {
            String summary = slaAlertService.scan();
            log.info("Collector SLA 扫描完成 {}", summary);
        } catch (Exception e) {
            log.error("Collector SLA 扫描失败", e);
        }
    }
}
