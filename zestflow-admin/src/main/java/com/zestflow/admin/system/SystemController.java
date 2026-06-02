package com.zestflow.admin.system;

import com.zestflow.admin.config.TenantModeConfig;
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
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

    private final Environment environment;
    private final TenantModeConfig tenantModeConfig;

    /**
     * 获取系统特性开关状态，前端据此控制菜单显隐；E2E 用于探测运行时租户/IP 模式
     */
    @GetMapping("/features")
    public Map<String, Object> getFeatures() {
        boolean playgroundEnabled = "true".equalsIgnoreCase(
                environment.getProperty("zestflow.playground.enabled", "false"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("playground", Map.of("enabled", playgroundEnabled));
        out.put("tenant", Map.of(
                "mode", tenantModeConfig.getMode() != null ? tenantModeConfig.getMode() : "single",
                "ipDemoMode", tenantModeConfig.getIpDemoMode() != null ? tenantModeConfig.getIpDemoMode() : "disabled"
        ));
        String registryToken = environment.getProperty("zestflow.admin.registry-token", "");
        out.put("security", Map.of(
                "registryTokenConfigured", registryToken != null && !registryToken.isBlank()
        ));
        return out;
    }
}
