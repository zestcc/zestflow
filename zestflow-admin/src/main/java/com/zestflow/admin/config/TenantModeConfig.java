package com.zestflow.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 多租户配置
 */
@Component
@ConfigurationProperties(prefix = "zestflow.tenant")
public class TenantModeConfig {

    /** 租户模式：single（单租户）/ multi（多租户） */
    private String mode = "single";

    /** IP 演示模式：enabled / disabled */
    private String ipDemoMode = "disabled";

    /** IP 租户非活跃超时（分钟），试玩滑动回收窗口 */
    private long ipTenantTimeoutMinutes = 60;

    /** 母版租户 ID — 试玩克隆模板 */
    private long templateTenantId = 1L;

    /** 是否执行试玩租户定时回收 */
    private boolean trialLifecycleEnabled = true;

    /** 是否开放 POST /api/public/tenants/provision */
    private boolean publicProvisionEnabled = false;

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getIpDemoMode() { return ipDemoMode; }
    public void setIpDemoMode(String ipDemoMode) { this.ipDemoMode = ipDemoMode; }
    public long getIpTenantTimeoutMinutes() { return ipTenantTimeoutMinutes; }
    public void setIpTenantTimeoutMinutes(long ipTenantTimeoutMinutes) { this.ipTenantTimeoutMinutes = ipTenantTimeoutMinutes; }
    public long getTemplateTenantId() { return templateTenantId; }
    public void setTemplateTenantId(long templateTenantId) { this.templateTenantId = templateTenantId; }
    public boolean isTrialLifecycleEnabled() { return trialLifecycleEnabled; }
    public void setTrialLifecycleEnabled(boolean trialLifecycleEnabled) { this.trialLifecycleEnabled = trialLifecycleEnabled; }
    public boolean isPublicProvisionEnabled() { return publicProvisionEnabled; }
    public void setPublicProvisionEnabled(boolean publicProvisionEnabled) { this.publicProvisionEnabled = publicProvisionEnabled; }
}
