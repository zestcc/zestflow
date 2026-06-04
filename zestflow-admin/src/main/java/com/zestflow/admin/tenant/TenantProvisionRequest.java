package com.zestflow.admin.tenant;

import lombok.Builder;
import lombok.Getter;

import java.time.Duration;

/**
 * 统一租户开户请求 — IP 试玩 / 公开 API / 管理员代开共用。
 */
@Getter
@Builder
public class TenantProvisionRequest {

    private final String name;
    private final String code;
    private final String description;
    /** {@link TenantTypes} */
    private final String tenantType;
    /** {@link ProvisionSources} */
    private final String provisionSource;
    /** 试玩滑动窗口 TTL；standard 可为 null */
    private final Duration ttl;
    /** IP 试玩时绑定 */
    private final String ipAddress;
    /** 克隆模板租户 ID，null 则用配置默认 */
    private final Long templateTenantId;
    private final String createdBy;

    public boolean isTrial() {
        return TenantTypes.TRIAL.equals(tenantType);
    }
}
