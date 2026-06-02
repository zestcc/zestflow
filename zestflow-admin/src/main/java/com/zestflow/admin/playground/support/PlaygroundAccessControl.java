package com.zestflow.admin.playground.support;

import com.zestflow.admin.config.TenantModeConfig;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.service.PermissionService;
import com.zestflow.admin.util.SecurityUtils;
import com.zestflow.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Playground 鉴权 — 与链/设计管理一致的 JWT + 应用级 RBAC
 */
@Component
@RequiredArgsConstructor
public class PlaygroundAccessControl {

    private final PermissionService permissionService;
    private final TenantModeConfig tenantModeConfig;

    /**
     * IP 演示模式：TenantIpFilter 已绑定租户，匿名会话允许只读访问试验场（租户行级隔离由 MP 插件保证）
     */
    public boolean isIpDemoTenantSession() {
        if (!"enabled".equals(tenantModeConfig.getIpDemoMode())) {
            return false;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        if (!(auth.getDetails() instanceof SecurityUtils.AuthDetails details)) {
            return false;
        }
        return details.userId() == null && details.currentTenantId() != null && details.currentTenantId() > 0;
    }

    /**
     * 校验当前用户对指定 appCode 的访问权限
     */
    public void requireAppPermission(String appCode, String requiredRole) {
        if (appCode == null || appCode.isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR);
        }
        if (isIpDemoTenantSession()) {
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (SecurityUtils.isSuperAdmin(auth)) {
            return;
        }
        Long userId = SecurityUtils.getUserId(auth);
        if (userId == null || !permissionService.hasAppPermission(userId, appCode, requiredRole)) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }
    }

    public boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && SecurityUtils.isSuperAdmin(auth);
    }

    public String currentUsername() {
        return SecurityUtils.getCurrentUsername();
    }
}
