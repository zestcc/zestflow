package com.zestflow.admin.runtime;

import com.zestflow.admin.config.AdminCacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * 启动时输出部署模式说明，并对常见误配给出提示。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AdminRuntimeStateConfiguration {

    private final AdminDeployProperties deployProperties;
    private final AdminCacheProperties cacheProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void logDeployMode() {
        if (deployProperties.isCluster()) {
            log.info("Admin deploy-mode=cluster：发布进度/链同步使用 Redis（需 spring.data.redis.*）");
        } else {
            log.info("Admin deploy-mode=standalone：发布进度/链同步使用本地内存，无需 Redis");
        }
        log.info("Admin 权限缓存类型={}", cacheProperties.getType());
    }

    @Slf4j
    @Configuration
    @Conditional(AdminDeployModeConditions.Cluster.class)
    @RequiredArgsConstructor
    static class ClusterRuntimeStateValidator {

        private final org.springframework.core.env.Environment environment;
        private final AdminCacheProperties cacheProperties;

        @EventListener(ApplicationReadyEvent.class)
        public void validateClusterConfig() {
            String host = environment.getProperty("spring.data.redis.host");
            if (!org.springframework.util.StringUtils.hasText(host)) {
                log.warn("deploy-mode=cluster 但未配置 spring.data.redis.host，"
                        + "运行时状态无法跨实例共享；请配置 Redis 或改回 deploy-mode=standalone");
            }
            if (!"redis".equalsIgnoreCase(cacheProperties.getType())) {
                log.warn("deploy-mode=cluster 但 cache.type={}（非 redis），"
                        + "多 Admin 副本间权限缓存可能不一致；生产建议 cache.type=redis",
                        cacheProperties.getType());
            }
        }
    }
}
