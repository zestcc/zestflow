package com.zestflow.admin.system;

import com.zestflow.admin.config.AdminCacheProperties;
import com.zestflow.admin.config.AdminRedisConditions;
import com.zestflow.admin.config.AiPlatformConfig;
import com.zestflow.admin.config.PlaygroundPlatformConfig;
import com.zestflow.admin.config.TenantPlatformConfig;
import com.zestflow.admin.runtime.AdminDeployProperties;
import com.zestflow.common.model.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统配置/特性查询 — 始终加载，不受开关影响
 */
@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class SystemController {

    private final Environment environment;
    private final TenantPlatformConfig tenantPlatformConfig;
    private final AdminDeployProperties deployProperties;
    private final AdminCacheProperties cacheProperties;
    private final AiPlatformConfig aiPlatformConfig;
    private final PlaygroundPlatformConfig playgroundPlatformConfig;
    private final ProductionReadinessService productionReadinessService;

    /**
     * 获取系统特性开关状态，前端/E2E 探测运行时配置
     */
    @GetMapping("/features")
    public Map<String, Object> getFeatures() {
        boolean playgroundEnabled = playgroundPlatformConfig.isEnabled();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("playground", Map.of("enabled", playgroundEnabled));
        out.put("copilot", Map.of("globallyEnabled", aiPlatformConfig.isEnabled()));
        out.put("tenant", Map.of(
                "mode", tenantPlatformConfig.getMode() != null ? tenantPlatformConfig.getMode() : "single",
                "ipDemoMode", tenantPlatformConfig.getIpDemoMode() != null ? tenantPlatformConfig.getIpDemoMode() : "disabled"
        ));
        String registryToken = environment.getProperty("zestflow.admin.registry-token", "");
        String executorAccessToken = environment.getProperty("zestflow.admin.executor-access-token", "");
        out.put("security", Map.of(
                "registryTokenConfigured", registryToken != null && !registryToken.isBlank(),
                "executorAccessTokenConfigured", executorAccessToken != null && !executorAccessToken.isBlank(),
                "collectorAccessTokenConfigured", environment.getProperty("zestflow.collector.access-token", "") != null
                        && !environment.getProperty("zestflow.collector.access-token", "").isBlank()
        ));
        out.put("admin", Map.of(
                "deployMode", deployProperties.getDeployMode(),
                "cacheType", cacheProperties.getType(),
                "redisRequired", AdminRedisConditions.isRedisInfrastructureRequired(environment)
        ));
        return out;
    }

    /** 生产发版 checklist（脚本 run-production-profile-checklist.ps1 消费） */
    @GetMapping("/production-readiness")
    public Result<Map<String, Object>> getProductionReadiness() {
        return Result.success(productionReadinessService.evaluate());
    }
}
