package com.zestflow.admin.config;

import com.zestflow.admin.runtime.AdminDeployProperties;
import com.zestflow.admin.service.CollectorRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Admin 运行时拓扑健康 — 单机无 Redis 仍报告 UP。
 */
@Component
@RequiredArgsConstructor
public class ZestFlowAdminHealthIndicator implements HealthIndicator {

    private final AdminDeployProperties deployProperties;
    private final AdminCacheProperties cacheProperties;
    private final CollectorRegistryService collectorRegistryService;
    private final Environment environment;

    @Override
    public Health health() {
        boolean redisRequired = AdminRedisConditions.isRedisInfrastructureRequired(environment);
        Health.Builder builder = Health.up()
                .withDetail("deployMode", deployProperties.getDeployMode())
                .withDetail("cacheType", cacheProperties.getType())
                .withDetail("redisInfrastructureRequired", redisRequired);

        if (redisRequired && !StringUtils.hasText(environment.getProperty("spring.data.redis.host"))) {
            builder.withDetail("redisConfigured", false)
                    .withDetail("warning", "需要 Redis 但未配置 spring.data.redis.host");
        } else {
            builder.withDetail("redisConfigured", !redisRequired || StringUtils.hasText(environment.getProperty("spring.data.redis.host")));
        }

        builder.withDetail("onlineCollectors", collectorRegistryService.listAllOnline().size());
        return builder.build();
    }
}
