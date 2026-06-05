package com.zestflow.admin.config;

import com.zestflow.admin.runtime.AdminClusterBuildSupport;
import com.zestflow.admin.runtime.AdminDeployProperties;
import com.zestflow.common.util.ProductionSecretGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 生产 profile 安全校验 — 公网部署须满足令牌、JWT、默认口令与演示开关等硬性要求。
 */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class AdminProductionGuard {

    private final Environment environment;
    private final AdminDeployProperties deployProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void validateProductionConfig() {
        boolean failed = false;

        if (ProductionSecretGuard.isWeakMachineToken(environment.getProperty("zestflow.admin.registry-token"))) {
            log.error("[prod] zestflow.admin.registry-token 未配置、过短或为模板占位符");
            failed = true;
        }

        if (ProductionSecretGuard.isWeakMachineToken(environment.getProperty("zestflow.admin.executor-access-token"))) {
            log.error("[prod] zestflow.admin.executor-access-token 未配置、过短或为模板占位符");
            failed = true;
        }

        if (ProductionSecretGuard.isWeakMachineToken(environment.getProperty("zestflow.collector.access-token"))) {
            log.error("[prod] zestflow.collector.access-token 未配置、过短或为模板占位符");
            failed = true;
        }

        if (ProductionSecretGuard.isDefaultJwtSecret(environment.getProperty("zestflow.jwt.secret"))) {
            log.error("[prod] zestflow.jwt.secret 仍为默认值、过短或未配置（至少 32 字符）");
            failed = true;
        }

        if (ProductionSecretGuard.isWeakAdminPassword(
                environment.getProperty("zestflow.admin.default-user.password"))) {
            log.error("[prod] zestflow.admin.default-user.password 禁止使用 admin123 或模板占位符");
            failed = true;
        }

        if (environment.getProperty("zestflow.playground.enabled", Boolean.class, Boolean.FALSE)) {
            log.error("[prod] zestflow.playground.enabled 必须为 false（公网勿暴露试验场）");
            failed = true;
        }

        if ("enabled".equalsIgnoreCase(environment.getProperty("zestflow.tenant.ip-demo-mode", "disabled"))) {
            log.error("[prod] zestflow.tenant.ip-demo-mode 必须为 disabled");
            failed = true;
        }

        if (!Boolean.TRUE.equals(environment.getProperty("spring.flyway.enabled", Boolean.class))) {
            log.error("[prod] spring.flyway.enabled 必须为 true（表结构由 Flyway db/migration 维护）");
            failed = true;
        }

        if (deployProperties.isCluster()) {
            if (!ProductionSecretGuard.hasText(environment.getProperty("spring.data.redis.host"))) {
                log.error("[prod] deploy-mode=cluster 必须配置 spring.data.redis.host");
                failed = true;
            }
            if (!AdminClusterBuildSupport.isClusterArtifact()) {
                log.error("[prod] deploy-mode=cluster 须使用 mvn -Pcluster 构建以启用 ShedLock 调度锁");
                failed = true;
            }
        } else {
            log.info("[prod] deploy-mode=standalone：无需 Redis，运行时状态与默认 Caffeine 缓存可正常工作");
        }

        if ("redis".equalsIgnoreCase(environment.getProperty("zestflow.admin.cache.type", "caffeine"))
                && !ProductionSecretGuard.hasText(environment.getProperty("spring.data.redis.host"))) {
            log.error("[prod] cache.type=redis 必须配置 spring.data.redis.host");
            failed = true;
        }

        if (failed) {
            throw new IllegalStateException("生产环境配置不完整，请修正上述 [prod] 日志项后重启");
        }
        log.info("[prod] 安全配置校验通过");
    }
}
