package com.zestflow.admin.system;

import com.zestflow.admin.runtime.AdminDeployProperties;
import com.zestflow.admin.config.PlaygroundPlatformConfig;
import com.zestflow.common.util.ProductionSecretGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生产发版 checklist — 供脚本与设置页探测，对标 PRODUCTION_ACCEPTANCE.md 手工项。
 */
@Service
@RequiredArgsConstructor
public class ProductionReadinessService {

    private final Environment environment;
    private final PlaygroundPlatformConfig playgroundPlatformConfig;
    private final AdminDeployProperties deployProperties;

    public Map<String, Object> evaluate() {
        boolean prodProfile = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        List<Map<String, Object>> items = new ArrayList<>();

        items.add(check("spring.profiles.active=prod", prodProfile,
                prodProfile ? "ok" : "当前非 prod profile"));
        items.add(check("registry-token", !ProductionSecretGuard.isWeakMachineToken(
                        environment.getProperty("zestflow.admin.registry-token")),
                tokenHint("zestflow.admin.registry-token")));
        items.add(check("executor-access-token", !ProductionSecretGuard.isWeakMachineToken(
                        environment.getProperty("zestflow.admin.executor-access-token")),
                tokenHint("zestflow.admin.executor-access-token")));
        items.add(check("collector.access-token", !ProductionSecretGuard.isWeakMachineToken(
                        environment.getProperty("zestflow.collector.access-token")),
                tokenHint("zestflow.collector.access-token")));
        items.add(check("jwt.secret", !ProductionSecretGuard.isDefaultJwtSecret(
                        environment.getProperty("zestflow.jwt.secret")),
                "≥32 字符且非默认值"));
        items.add(check("playground.disabled", !playgroundPlatformConfig.isEnabled(),
                playgroundPlatformConfig.isEnabled() ? "生产建议关闭" : "已关闭"));
        items.add(check("deploy-mode", !"cluster".equals(deployProperties.getDeployMode())
                        || ProductionSecretGuard.hasText(environment.getProperty("spring.data.redis.host")),
                "cluster 模式需 Redis"));

        boolean ssoEnabled = Boolean.parseBoolean(environment.getProperty("zestflow.sso.enabled", "false"));
        if (ssoEnabled) {
            items.add(check("sso.client-secret", !ProductionSecretGuard.isWeakOAuthClientSecret(
                            environment.getProperty("zestflow.sso.client-secret")),
                    "SSO 启用时必填"));
            items.add(check("sso.redirect-uri", ProductionSecretGuard.hasText(
                            environment.getProperty("zestflow.sso.redirect-uri")),
                    "SSO 启用时必填"));
            items.add(check("sso.client-id", ProductionSecretGuard.hasText(
                            environment.getProperty("zestflow.sso.client-id")),
                    "SSO 启用时必填"));
        }

        long failed = items.stream().filter(i -> !(Boolean) i.get("ok")).count();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("prodProfile", prodProfile);
        out.put("ready", failed == 0);
        out.put("failedCount", failed);
        out.put("items", items);
        return out;
    }

    private Map<String, Object> check(String name, boolean ok, String note) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("ok", ok);
        item.put("note", note);
        return item;
    }

    private String tokenHint(String key) {
        return ProductionSecretGuard.isWeakMachineToken(environment.getProperty(key))
                ? "未配置或为占位符（开发环境 BB-02/03 开放）" : "已配置";
    }
}
