package com.zestflow.admin.controller;

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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DesignControllerTest {

    @Mock private ExecutorProxyService proxyService;
    @Mock private PermissionService permissionService;
    @Mock private Authentication authentication;

    private DesignController designController;

    @BeforeEach
    void setUp() {
        designController = new DesignController(proxyService, permissionService);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_requiresViewerPermission() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn(new SecurityUtils.AuthDetails(2L, false, 1L));
        when(permissionService.hasAppPermission(2L, "app-a", "APP_VIEWER")).thenReturn(true);
        when(proxyService.getFromExecutor(eq("app-a"), anyString(), anyString()))
                .thenReturn("{\"records\":[]}");

        designController.listByAppCode("app-a", null, null, 1, 10);

        verify(permissionService).hasAppPermission(2L, "app-a", "APP_VIEWER");
    }

    @Test
    void getByCode_requiresViewerPermission() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn(new SecurityUtils.AuthDetails(2L, false, 1L));
        when(permissionService.hasAppPermission(2L, "app-a", "APP_VIEWER")).thenReturn(false);

        assertThatThrownBy(() -> designController.getByCode("DSN001", "app-a"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PERMISSION_DENIED);
    }

    @Test
    void create_requiresEditorPermission() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn(new SecurityUtils.AuthDetails(2L, false, 1L));
        when(permissionService.hasAppPermission(2L, "app-a", "APP_EDITOR")).thenReturn(true);
        when(proxyService.executeOnExecutor(eq("app-a"), anyString(), anyString(), anyString()))
                .thenReturn("{\"code\":200}");

        designController.create("{\"appCode\":\"app-a\",\"name\":\"new-design\"}");

        verify(permissionService).hasAppPermission(2L, "app-a", "APP_EDITOR");
    }

    @Test
    void create_missingAppCode_stillProceeds() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn(new SecurityUtils.AuthDetails(1L, true, 1L));

        designController.create("{\"name\":\"no-appcode-design\"}");

        verify(proxyService).executeOnExecutor(isNull(), eq("POST"), eq("/api/designs"), anyString());
    }

    @Test
    void update_requiresEditorPermission() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn(new SecurityUtils.AuthDetails(2L, false, 1L));
        when(permissionService.hasAppPermission(2L, "app-a", "APP_EDITOR")).thenReturn(true);
        when(proxyService.executeOnExecutor(eq("app-a"), anyString(), anyString(), anyString()))
                .thenReturn("{\"code\":200}");

        designController.update("DSN001", "{\"appCode\":\"app-a\",\"name\":\"updated\"}");

        verify(permissionService).hasAppPermission(2L, "app-a", "APP_EDITOR");
    }

    @Test
    void saveGraph_requiresEditorPermission() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn(new SecurityUtils.AuthDetails(2L, false, 1L));
        when(permissionService.hasAppPermission(2L, "app-a", "APP_EDITOR")).thenReturn(true);
        when(proxyService.executeOnExecutor(eq("app-a"), anyString(), anyString(), anyString()))
                .thenReturn("{\"code\":200}");

        designController.saveGraph("DSN001", "{\"appCode\":\"app-a\",\"graphData\":\"{}\"}");

        verify(permissionService).hasAppPermission(2L, "app-a", "APP_EDITOR");
    }

    @Test
    void saveGraph_missingAppCode_returns400() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);

        String result = designController.saveGraph("DSN001", "{\"name\":\"no-appcode\"}");

        assertThat(result).contains("400");
        verify(proxyService, never()).executeOnExecutor(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void delete_requiresAdminPermission() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn(new SecurityUtils.AuthDetails(2L, false, 1L));
        when(permissionService.hasAppPermission(2L, "app-a", "APP_ADMIN")).thenReturn(true);
        when(proxyService.executeOnExecutor(eq("app-a"), anyString(), anyString(), any()))
                .thenReturn("{\"code\":200}");

        designController.delete("DSN001", "app-a");

        verify(permissionService).hasAppPermission(2L, "app-a", "APP_ADMIN");
    }

    @Test
    void toggleStatus_requiresEditorPermission() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn(new SecurityUtils.AuthDetails(2L, false, 1L));
        when(permissionService.hasAppPermission(2L, "app-a", "APP_EDITOR")).thenReturn(true);
        when(proxyService.executeOnExecutor(eq("app-a"), anyString(), anyString(), anyString()))
                .thenReturn("{\"code\":200}");

        designController.toggleStatus("DSN001", "app-a");

        verify(permissionService).hasAppPermission(2L, "app-a", "APP_EDITOR");
    }

    @Test
    void getBindings_requiresViewerPermission() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn(new SecurityUtils.AuthDetails(2L, false, 1L));
        when(permissionService.hasAppPermission(2L, "app-a", "APP_VIEWER")).thenReturn(true);
        when(proxyService.getFromExecutor(eq("app-a"), anyString(), anyString()))
                .thenReturn("{\"records\":[]}");

        designController.getBindings("DSN001", "app-a");

        verify(permissionService).hasAppPermission(2L, "app-a", "APP_VIEWER");
    }

    @Test
    void getBindable_requiresViewerPermission() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn(new SecurityUtils.AuthDetails(2L, false, 1L));
        when(permissionService.hasAppPermission(2L, "app-a", "APP_VIEWER")).thenReturn(true);
        when(proxyService.getFromExecutor(eq("app-a"), anyString(), anyString()))
                .thenReturn("{\"records\":[]}");

        designController.getBindable("DSN001", "app-a");

        verify(permissionService).hasAppPermission(2L, "app-a", "APP_VIEWER");
    }

    @Test
    void bind_requiresEditorPermission() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn(new SecurityUtils.AuthDetails(2L, false, 1L));
        when(permissionService.hasAppPermission(2L, "app-a", "APP_EDITOR")).thenReturn(true);
        when(proxyService.executeOnExecutor(eq("app-a"), anyString(), anyString(), anyString()))
                .thenReturn("{\"code\":200}");

        designController.bind("DSN001", "app-a", "{\"chainCode\":\"CHN001\"}");

        verify(permissionService).hasAppPermission(2L, "app-a", "APP_EDITOR");
    }

    @Test
    void unbind_requiresEditorPermission() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn(new SecurityUtils.AuthDetails(2L, false, 1L));
        when(permissionService.hasAppPermission(2L, "app-a", "APP_EDITOR")).thenReturn(true);
        when(proxyService.executeOnExecutor(eq("app-a"), anyString(), anyString(), any()))
                .thenReturn("{\"code\":200}");

        designController.unbind("DSN001", "CHN001", "app-a");

        verify(permissionService).hasAppPermission(2L, "app-a", "APP_EDITOR");
    }

    @Test
    void unauthenticated_throwsUnauthorized() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);

        assertThatThrownBy(() -> designController.listByAppCode("app-a", null, null, 1, 10))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }

    @Test
    void nullAppCode_skipsPermissionCheck() {
        SecurityContextHolder.getContext().setAuthentication(null);

        // null appCode should skip permission check, proxy should get null for appCode
        when(proxyService.getFromExecutor(isNull(), anyString(), anyString()))
                .thenReturn("{\"records\":[]}");

        designController.listByAppCode(null, null, null, 1, 10);

        verify(permissionService, never()).hasAppPermission(anyLong(), anyString(), anyString());
    }
}
