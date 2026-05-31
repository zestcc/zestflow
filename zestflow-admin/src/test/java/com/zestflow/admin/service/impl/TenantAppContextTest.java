package com.zestflow.admin.service.impl;

import com.zestflow.admin.service.PermissionService;
import com.zestflow.admin.service.TenantAppContext;
import com.zestflow.admin.util.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantAppContextTest {

    @Mock private PermissionService permissionService;
    @Mock private Authentication authentication;

    private TenantAppContext tenantAppContext;

    @BeforeEach
    void setUp() {
        // 用反射设置 @Value 字段
        tenantAppContext = new TenantAppContext(permissionService);
        setField(tenantAppContext, "defaultTenantId", 1L);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ==================== getCurrentTenantId ====================

    @Test
    void getCurrentTenantId_returnsDefault() {
        assertThat(tenantAppContext.getCurrentTenantId()).isEqualTo(1L);
    }

    // ==================== getCurrentUserAppCodes ====================

    @Test
    void getCurrentUserAppCodes_noAuth_returnsEmptySet() {
        SecurityContextHolder.getContext().setAuthentication(null);

        Set<String> codes = tenantAppContext.getCurrentUserAppCodes();

        assertThat(codes).isEmpty();
    }

    @Test
    void getCurrentUserAppCodes_superAdmin_returnsEmptySet() {
        when(authentication.isAuthenticated()).thenReturn(true);
        SecurityUtils.AuthDetails details = new SecurityUtils.AuthDetails(1L, true);
        when(authentication.getDetails()).thenReturn(details);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        Set<String> codes = tenantAppContext.getCurrentUserAppCodes();

        assertThat(codes).isEmpty();
        verify(permissionService, never()).getAccessibleAppCodes(anyLong());
    }

    @Test
    void getCurrentUserAppCodes_normalUser_returnsAccessibleCodes() {
        when(authentication.isAuthenticated()).thenReturn(true);
        SecurityUtils.AuthDetails details = new SecurityUtils.AuthDetails(2L, false);
        when(authentication.getDetails()).thenReturn(details);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(permissionService.getAccessibleAppCodes(2L)).thenReturn(Set.of("app-a", "app-b"));

        Set<String> codes = tenantAppContext.getCurrentUserAppCodes();

        assertThat(codes).containsExactlyInAnyOrder("app-a", "app-b");
        verify(permissionService).getAccessibleAppCodes(2L);
    }

    @Test
    void getCurrentUserAppCodes_normalUserNoApps_returnsEmptySet() {
        when(authentication.isAuthenticated()).thenReturn(true);
        SecurityUtils.AuthDetails details = new SecurityUtils.AuthDetails(2L, false);
        when(authentication.getDetails()).thenReturn(details);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(permissionService.getAccessibleAppCodes(2L)).thenReturn(Collections.emptySet());

        Set<String> codes = tenantAppContext.getCurrentUserAppCodes();

        assertThat(codes).isEmpty();
    }

    @Test
    void getCurrentUserAppCodes_authenticationException_returnsEmptySet() {
        when(authentication.isAuthenticated()).thenThrow(new RuntimeException("Auth error"));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        Set<String> codes = tenantAppContext.getCurrentUserAppCodes();

        assertThat(codes).isEmpty();
    }

    // ==================== hasEditPermission ====================

    @Test
    void hasEditPermission_noAuth_returnsFalse() {
        SecurityContextHolder.getContext().setAuthentication(null);

        assertThat(tenantAppContext.hasEditPermission("app-a")).isFalse();
    }

    @Test
    void hasEditPermission_superAdmin_returnsTrue() {
        when(authentication.isAuthenticated()).thenReturn(true);
        SecurityUtils.AuthDetails details = new SecurityUtils.AuthDetails(1L, true);
        when(authentication.getDetails()).thenReturn(details);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(tenantAppContext.hasEditPermission("app-a")).isTrue();
        verify(permissionService, never()).hasAppPermission(anyLong(), anyString(), anyString());
    }

    @Test
    void hasEditPermission_normalUserWithEditorRole_returnsTrue() {
        when(authentication.isAuthenticated()).thenReturn(true);
        SecurityUtils.AuthDetails details = new SecurityUtils.AuthDetails(2L, false);
        when(authentication.getDetails()).thenReturn(details);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(permissionService.hasAppPermission(2L, "app-a", "APP_EDITOR")).thenReturn(true);

        assertThat(tenantAppContext.hasEditPermission("app-a")).isTrue();
    }

    @Test
    void hasEditPermission_normalUserWithViewerRole_returnsFalse() {
        when(authentication.isAuthenticated()).thenReturn(true);
        SecurityUtils.AuthDetails details = new SecurityUtils.AuthDetails(2L, false);
        when(authentication.getDetails()).thenReturn(details);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(permissionService.hasAppPermission(2L, "app-a", "APP_EDITOR")).thenReturn(false);

        assertThat(tenantAppContext.hasEditPermission("app-a")).isFalse();
    }

    @Test
    void hasEditPermission_unauthenticated_returnsFalse() {
        when(authentication.isAuthenticated()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(tenantAppContext.hasEditPermission("app-a")).isFalse();
    }

    @Test
    void hasEditPermission_nullUserId_returnsFalse() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn("not-an-auth-details");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(tenantAppContext.hasEditPermission("app-a")).isFalse();
    }

    // ==================== 权限校验跨线程上下文隔离 ====================

    @Test
    void getCurrentUserAppCodes_crossThread_contextIsolation() throws Exception {
        // 主线程设置认证
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 子线程应无认证上下文
        String[] childResult = new String[1];
        Thread child = new Thread(() -> {
            Set<String> codes = tenantAppContext.getCurrentUserAppCodes();
            childResult[0] = codes.isEmpty() ? "empty" : "has-values";
        });
        child.start();
        child.join(2000);

        assertThat(childResult[0]).isEqualTo("empty");
    }
}
