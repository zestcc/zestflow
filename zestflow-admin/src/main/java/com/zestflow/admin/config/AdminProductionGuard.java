package com.zestflow.admin.config;

import com.zestflow.admin.runtime.AdminClusterBuildSupport;
import com.zestflow.admin.runtime.AdminDeployProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 生产 profile 安全校验 — 单机不要求 Redis；仅校验令牌/JWT 等安全项。
 */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class AdminProductionGuard {

    private static final String DEFAULT_JWT_SNIPPET = "Change_Me_In_Production";

    private final Environment environment;
    private final AdminDeployProperties deployProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void validateProductionConfig() {
        boolean failed = false;

        String registryToken = environment.getProperty("zestflow.admin.registry-token", "");
        if (!StringUtils.hasText(registryToken)) {
            log.error("[prod] zestflow.admin.registry-token 未配置，机器接口处于开放状态");
            failed = true;
        }

        String jwtSecret = environment.getProperty("zestflow.jwt.secret", "");
        if (!StringUtils.hasText(jwtSecret) || jwtSecret.contains(DEFAULT_JWT_SNIPPET)) {
            log.error("[prod] zestflow.jwt.secret 仍为默认值或未配置");
            failed = true;
        }

        if (deployProperties.isCluster()) {
            String redisHost = environment.getProperty("spring.data.redis.host");
            if (!StringUtils.hasText(redisHost)) {
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
                && !StringUtils.hasText(environment.getProperty("spring.data.redis.host"))) {
            log.error("[prod] cache.type=redis 必须配置 spring.data.redis.host");
            failed = true;
        }

        if (failed) {
            throw new IllegalStateException("生产环境配置不完整，请修正上述 [prod] 日志项后重启");
        }
    }
}
