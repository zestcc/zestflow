package com.zestflow.admin.config;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TenantPlatformConfig {

    private final PlatformConfigReader platformConfig;
    private final TenantModeConfig yaml;

    public long getIpTenantTimeoutMinutes() {
        return platformConfig.getLong(SysConfigKeys.TENANT_IP_TIMEOUT_MINUTES, yaml::getIpTenantTimeoutMinutes);
    }

    public boolean isTrialLifecycleEnabled() {
        return platformConfig.getBoolean(SysConfigKeys.TENANT_TRIAL_LIFECYCLE_ENABLED, yaml::isTrialLifecycleEnabled);
    }

    public boolean isPublicProvisionEnabled() {
        return platformConfig.getBoolean(SysConfigKeys.TENANT_PUBLIC_PROVISION_ENABLED, yaml::isPublicProvisionEnabled);
    }

    /** 仍来自 yaml（架构级） */
    public String getMode() {
        return yaml.getMode();
    }

    public String getIpDemoMode() {
        return yaml.getIpDemoMode();
    }

    public long getTemplateTenantId() {
        return yaml.getTemplateTenantId();
    }
}
