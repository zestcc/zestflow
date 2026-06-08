package com.zestflow.admin.ai;

import com.zestflow.admin.config.TenantContextHolder;
import lombok.Getter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 跨线程传递 Security + Tenant 上下文（SSE 异步执行）
 */
@Getter
public final class AiCopilotRequestContext {

    private final Authentication authentication;
    private final Long tenantId;

    public AiCopilotRequestContext() {
        this.authentication = SecurityContextHolder.getContext().getAuthentication();
        this.tenantId = TenantContextHolder.getTenantId();
    }

    public void apply() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        if (tenantId != null) {
            TenantContextHolder.setTenantId(tenantId);
        }
    }

    public void clear() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }
}
