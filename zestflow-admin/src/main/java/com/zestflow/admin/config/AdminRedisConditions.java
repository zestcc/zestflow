package com.zestflow.admin.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Admin Redis 基础设施条件 — 仅 cluster 或 cache.type=redis 时创建连接，单机默认不连 Redis。
 */
public final class AdminRedisConditions {

    private AdminRedisConditions() {
    }

    public static final class InfrastructureRequired implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return isRedisInfrastructureRequired(context.getEnvironment());
        }
    }

    public static boolean isRedisInfrastructureRequired(Environment environment) {
        if (environment == null) {
            return false;
        }
        String deployMode = environment.getProperty("zestflow.admin.deploy-mode");
        if (StringUtils.hasText(deployMode)) {
            if (isClusterAlias(deployMode)) {
                return true;
            }
            if (isStandaloneAlias(deployMode)) {
                return isCacheRedis(environment);
            }
        }
        if ("redis".equalsIgnoreCase(environment.getProperty("zestflow.admin.runtime-state.type"))) {
            return true;
        }
        return isCacheRedis(environment);
    }

    private static boolean isCacheRedis(Environment environment) {
        return "redis".equalsIgnoreCase(environment.getProperty("zestflow.admin.cache.type"));
    }

    private static boolean isClusterAlias(String mode) {
        return "cluster".equalsIgnoreCase(mode) || "multi".equalsIgnoreCase(mode);
    }

    private static boolean isStandaloneAlias(String mode) {
        return "standalone".equalsIgnoreCase(mode) || "single".equalsIgnoreCase(mode);
    }
}
