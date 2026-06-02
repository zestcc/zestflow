package com.zestflow.admin.runtime;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Admin 部署模式条件 — 仅用于运行时状态（发布进度、链 sync），与 cache.type 无关。
 */
public final class AdminDeployModeConditions {

    private AdminDeployModeConditions() {
    }

    public static final class Standalone implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return !isClusterMode(context);
        }
    }

    public static final class Cluster implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return isClusterMode(context);
        }
    }

    static boolean isClusterMode(ConditionContext context) {
        String deployMode = context.getEnvironment().getProperty("zestflow.admin.deploy-mode");
        if (StringUtils.hasText(deployMode)) {
            return isClusterAlias(deployMode);
        }
        String legacyType = context.getEnvironment().getProperty("zestflow.admin.runtime-state.type");
        return "redis".equalsIgnoreCase(legacyType);
    }

    static String normalizeDeployMode(String deployMode) {
        if (!StringUtils.hasText(deployMode)) {
            return "standalone";
        }
        if (isClusterAlias(deployMode)) {
            return "cluster";
        }
        if (isStandaloneAlias(deployMode)) {
            return "standalone";
        }
        return deployMode.trim().toLowerCase();
    }

    private static boolean isClusterAlias(String mode) {
        return "cluster".equalsIgnoreCase(mode) || "multi".equalsIgnoreCase(mode);
    }

    private static boolean isStandaloneAlias(String mode) {
        return "standalone".equalsIgnoreCase(mode) || "single".equalsIgnoreCase(mode);
    }
}
