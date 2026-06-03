package com.zestflow.executor.config;

import com.zestflow.common.util.ProductionSecretGuard;
import com.zestflow.executor.registry.ExecutorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * 生产 profile — Executor Netty / 注册接口令牌校验。
 */
@Slf4j
@RequiredArgsConstructor
public class ExecutorProductionGuard {

    private final ExecutorProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void validateProductionConfig() {
        boolean failed = false;

        if (ProductionSecretGuard.isWeakMachineToken(properties.getAccessToken())) {
            log.error("[prod] zestflow.executor.access-token 未配置、过短或为模板占位符");
            failed = true;
        }
        if (ProductionSecretGuard.isWeakMachineToken(properties.getRegistryToken())) {
            log.error("[prod] zestflow.executor.registry-token 未配置、过短或为模板占位符");
            failed = true;
        }

        if (failed) {
            throw new IllegalStateException("Executor 生产环境配置不完整，请修正上述 [prod] 日志项后重启");
        }
        log.info("[prod] Executor 安全配置校验通过");
    }
}
