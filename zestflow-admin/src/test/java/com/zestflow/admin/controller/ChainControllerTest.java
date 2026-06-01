package com.zestflow.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.admin.client.CollectorClient;
import com.zestflow.admin.client.ExecutorProxyService;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.service.PermissionService;
import com.zestflow.admin.util.SecurityUtils;
import com.zestflow.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChainControllerTest {

    @Mock private ExecutorProxyService proxyService;
    @Mock private PermissionService permissionService;
    @Mock private CollectorClient collectorClient;
    @Mock private Authentication authentication;

    private ChainController chainController;

    @BeforeEach
    void setUp() {
        chainController = new ChainController(proxyService, permissionService, collectorClient);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ==================== listByAppCode 权限 ====================

    @Test
    void list_superAdmin_bypassesPermissionCheck() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn(new SecurityUtils.AuthDetails(1L, true, 1L));
        when(proxyService.getFromExecutor(eq("app-a"), anyString(), anyString()))
                .thenReturn("{\"records\":[],\"total\":0}");

        chainController.listByAppCode("app-a", null, null, 1, 10);

        verify(permissionService, never()).hasAppPermission(anyLong(), anyString(), anyString());
    }

    @Test
    void list_normalUser_withViewerPermission() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn(new SecurityUtils.AuthDetails(2L, false, 1L));
        when(permissionService.hasAppPermission(2L, "app-a", "APP_VIEWER")).thenReturn(true);
        when(proxyService.getFromExecutor(eq("app-a"), anyString(), anyString()))
                .thenReturn("{\"records\":[],\"total\":0}");

        chainController.listByAppCode("app-a", null, null, 1, 10);

        verify(permissionService).hasAppPermission(2L, "app-a", "APP_VIEWER");
    }

    @Test
    void list_normalUser_withoutPermission_throws() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn(new SecurityUtils.AuthDetails(2L, false, 1L));
        when(permissionService.hasAppPermission(2L, "app-a", "APP_VIEWER")).thenReturn(false);

        assertThatThrownBy(() -> chainController.listByAppCode("app-a", null, null, 1, 10))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PERMISSION_DENIED);
    }

    @Test
    void list_unauthenticated_throws() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);

        assertThatThrownBy(() -> chainController.listByAppCode("app-a", null, null, 1, 10))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }

    // ==================== create 权限 ====================

    @Test
    void create_requiresEditorPermission() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn(new SecurityUtils.AuthDetails(2L, false, 1L));
        when(permissionService.hasAppPermission(2L, "app-a", "APP_EDITOR")).thenReturn(true);
        when(proxyService.executeOnExecutor(eq("app-a"), anyString(), anyString(), anyString()))
                .thenReturn("{\"code\":200}");

        chainController.create("{\"appCode\":\"app-a\",\"name\":\"test\"}");

        verify(permissionService).hasAppPermission(2L, "app-a", "APP_EDITOR");
    }

    // ==================== delete 权限 ====================

    @Test
    void delete_requiresAdminPermission() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn(new SecurityUtils.AuthDetails(2L, false, 1L));
        when(permissionService.hasAppPermission(2L, "app-a", "APP_ADMIN")).thenReturn(true);
        when(proxyService.executeOnExecutor(eq("app-a"), anyString(), anyString(), any()))
                .thenReturn("{\"code\":200}");

        chainController.delete("chain-1", "app-a");

        verify(permissionService).hasAppPermission(2L, "app-a", "APP_ADMIN");
    }

    // ==================== update 权限 ====================

    @Test
    void update_requiresEditorPermission() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn(new SecurityUtils.AuthDetails(2L, false, 1L));
        when(permissionService.hasAppPermission(2L, "app-a", "APP_EDITOR")).thenReturn(true);
        when(proxyService.executeOnExecutor(eq("app-a"), anyString(), anyString(), anyString()))
                .thenReturn("{\"code\":200}");

        chainController.update("chain-1", "{\"appCode\":\"app-a\",\"name\":\"updated\"}");

        verify(permissionService).hasAppPermission(2L, "app-a", "APP_EDITOR");
    }

    // ==================== publish 权限 ====================

    @Test
    void publish_requiresAdminPermission() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        SecurityUtils.AuthDetails details = new SecurityUtils.AuthDetails(1L, true, 1L);
        when(authentication.getDetails()).thenReturn(details);
        when(proxyService.resolveAllExecutorUrls(anyString())).thenReturn(java.util.List.of());
        when(proxyService.getFromExecutor(anyString(), anyString(), anyString()))
                .thenReturn("{\"code\":200,\"designCode\":\"DSN001\"}")
                .thenReturn("{\"code\":200,\"graphData\":\"{}\",\"chainData\":\"\"}");

        chainController.publish("chain-1", "app-a");

        verify(proxyService).resolveAllExecutorUrls("app-a");
    }

    // ==================== activeCodes / getByCode / versions ====================

    @Test
    void fetchActiveCodes_requiresViewerPermission() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn(new SecurityUtils.AuthDetails(2L, false, 1L));
        when(permissionService.hasAppPermission(2L, "app-a", "APP_VIEWER")).thenReturn(true);

        chainController.fetchActiveCodes("app-a");

        verify(permissionService).hasAppPermission(2L, "app-a", "APP_VIEWER");
    }

    @Test
    void getByCode_requiresViewerPermission() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn(new SecurityUtils.AuthDetails(2L, false, 1L));
        when(permissionService.hasAppPermission(2L, "app-a", "APP_VIEWER")).thenReturn(false);

        assertThatThrownBy(() -> chainController.getByCode("chain-1", "app-a"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PERMISSION_DENIED);
    }

    // ==================== restore 权限 ====================

    @Test
    void rollback_requiresAdminPermission() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        SecurityUtils.AuthDetails details = new SecurityUtils.AuthDetails(1L, true, 1L);
        when(authentication.getDetails()).thenReturn(details);

        chainController.rollback("chain-1", 1, "{\"appCode\":\"app-a\"}");

        verify(proxyService).executeOnExecutor(anyString(), anyString(), anyString(), anyString());
    }

    // ==================== toggleStatus 权限 ====================

    @Test
    void toggleStatus_requiresEditorPermission() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn(new SecurityUtils.AuthDetails(2L, false, 1L));
        when(permissionService.hasAppPermission(2L, "app-a", "APP_EDITOR")).thenReturn(true);
        when(proxyService.executeOnExecutor(eq("app-a"), anyString(), anyString(), anyString()))
                .thenReturn("{\"code\":200}");

        chainController.toggleStatus("chain-1", "app-a");

        verify(permissionService).hasAppPermission(2L, "app-a", "APP_EDITOR");
    }
}
