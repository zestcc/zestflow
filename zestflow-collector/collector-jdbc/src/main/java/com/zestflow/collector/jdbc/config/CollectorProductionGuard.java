package com.zestflow.collector.jdbc.config;

import com.zestflow.common.util.ProductionSecretGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * 生产 profile — Collector 查询 API 令牌校验。
 */
@Slf4j
@RequiredArgsConstructor
public class CollectorProductionGuard {

    private final CollectorProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void validateProductionConfig() {
        if (ProductionSecretGuard.isWeakMachineToken(properties.getAccessToken())) {
            log.error("[prod] zestflow.collector.access-token 未配置、过短或为模板占位符");
            throw new IllegalStateException("Collector 生产环境配置不完整，请修正上述 [prod] 日志项后重启");
        }
        log.info("[prod] Collector 安全配置校验通过");
    }
}
