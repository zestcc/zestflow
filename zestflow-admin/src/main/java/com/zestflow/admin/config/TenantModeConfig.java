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

    /** IP 租户非活跃超时（分钟） */
    private long ipTenantTimeoutMinutes = 60;

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getIpDemoMode() { return ipDemoMode; }
    public void setIpDemoMode(String ipDemoMode) { this.ipDemoMode = ipDemoMode; }
    public long getIpTenantTimeoutMinutes() { return ipTenantTimeoutMinutes; }
    public void setIpTenantTimeoutMinutes(long ipTenantTimeoutMinutes) { this.ipTenantTimeoutMinutes = ipTenantTimeoutMinutes; }
}
