package com.zestflow.admin.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.admin.runtime.AdminDeployProperties;
import com.zestflow.admin.service.CollectorRegistryService;
import com.zestflow.common.constant.RegistryConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Admin 运行时拓扑健康 — 单机无 Redis 仍报告 UP；cluster 缺 Redis 或零在线节点时 DEGRADED。
 */
@Component
@RequiredArgsConstructor
public class ZestFlowAdminHealthIndicator implements HealthIndicator {

    /** 自定义降级状态（Spring Boot 无内置 DEGRADED，对标 K8s Degraded） */
    static final Status DEGRADED = new Status("DEGRADED");

    private final AdminDeployProperties deployProperties;
    private final AdminCacheProperties cacheProperties;
    private final CollectorRegistryService collectorRegistryService;
    private final ExecutorRegistryMapper executorRegistryMapper;
    private final AdminNodeReachabilityService reachabilityService;
    private final Environment environment;

    @Override
    public Health health() {
        boolean redisRequired = AdminRedisConditions.isRedisInfrastructureRequired(environment);
        boolean redisConfigured = !redisRequired || StringUtils.hasText(environment.getProperty("spring.data.redis.host"));
        int onlineCollectors = collectorRegistryService.listAllOnline().size();
        long onlineExecutors = executorRegistryMapper.selectCount(
                new LambdaQueryWrapper<ExecutorRegistryPO>()
                        .eq(ExecutorRegistryPO::getStatus, RegistryConstants.STATUS_ONLINE));

        AdminNodeReachabilityService.NodeProbeSummary probe = reachabilityService.probeRegisteredNodes();

        Health.Builder builder = Health.status(resolveStatus(
                        redisRequired, redisConfigured, onlineCollectors, onlineExecutors, probe))
                .withDetail("deployMode", deployProperties.getDeployMode())
                .withDetail("cacheType", cacheProperties.getType())
                .withDetail("redisInfrastructureRequired", redisRequired)
                .withDetail("redisConfigured", redisConfigured)
                .withDetail("onlineCollectors", onlineCollectors)
                .withDetail("onlineExecutors", onlineExecutors);

        if (probe.enabled()) {
            builder.withDetail("executorsReachable", probe.executorsReachable())
                    .withDetail("executorsUnreachable", probe.executorsUnreachable())
                    .withDetail("collectorsReachable", probe.collectorsReachable())
                    .withDetail("collectorsUnreachable", probe.collectorsUnreachable());
            if (!probe.unreachableExecutorIds().isEmpty()) {
                builder.withDetail("unreachableExecutors", probe.unreachableExecutorIds());
            }
            if (!probe.unreachableCollectorIds().isEmpty()) {
                builder.withDetail("unreachableCollectors", probe.unreachableCollectorIds());
            }
        }

        if (redisRequired && !redisConfigured) {
            builder.withDetail("warning", "需要 Redis 但未配置 spring.data.redis.host");
        }
        if (onlineCollectors == 0) {
            builder.withDetail("collectorWarning", "无在线采集器，日志/轨迹查询不可用");
        }
        if (onlineExecutors == 0) {
            builder.withDetail("executorWarning", "无在线执行器，链执行与调度不可用");
        }
        if (probe.enabled() && probe.hasUnreachableNodes()) {
            builder.withDetail("probeWarning", "注册在线但 HTTP 探活失败的节点");
        }
        return builder.build();
    }

    private static Status resolveStatus(boolean redisRequired,
                                        boolean redisConfigured,
                                        int onlineCollectors,
                                        long onlineExecutors,
                                        AdminNodeReachabilityService.NodeProbeSummary probe) {
        if (redisRequired && !redisConfigured) {
            return Status.DOWN;
        }
        if (onlineCollectors == 0 || onlineExecutors == 0) {
            return DEGRADED;
        }
        if (probe.enabled() && probe.hasUnreachableNodes()) {
            return DEGRADED;
        }
        return Status.UP;
    }
}
