package com.zestflow.admin.service;

import com.zestflow.admin.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;

/**
 * 租户 & 应用上下文工具
 * 提供当前登录用户的 tenantId 和可访问 appCode 集合
 */
@Component
@RequiredArgsConstructor
public class TenantAppContext {

    @Value("${zestflow.admin.tenant-id:1}")
    private Long defaultTenantId;

    private final PermissionService permissionService;

    /**
     * 获取当前租户ID
     */
    public Long getCurrentTenantId() {
        return defaultTenantId;
    }

    /**
     * 获取当前用户可访问的 appCode 集合
     * 超级管理员返回空集（不过滤），普通用户返回 user_app_role 中授权的 appCode
     */
    public Set<String> getCurrentUserAppCodes() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return Collections.emptySet();
            }
            boolean isSuperAdmin = SecurityUtils.isSuperAdmin(auth);
            if (isSuperAdmin) {
                return Collections.emptySet();
            }
            Long userId = SecurityUtils.getUserId(auth);
            if (userId == null) {
                return Collections.emptySet();
            }
            return permissionService.getAccessibleAppCodes(userId);
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    /**
     * 判断当前用户是否有指定 appCode 的编辑权限
     */
    public boolean hasEditPermission(String appCode) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return false;
            }
            boolean isSuperAdmin = SecurityUtils.isSuperAdmin(auth);
            if (isSuperAdmin) {
                return true;
            }
            Long userId = SecurityUtils.getUserId(auth);
            if (userId == null) {
                return false;
            }
            return permissionService.hasAppPermission(userId, appCode, "APP_EDITOR");
        } catch (Exception e) {
            return false;
        }
    }
}
