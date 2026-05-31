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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ComponentControllerTest {

    @Mock private ExecutorProxyService proxyService;
    @Mock private PermissionService permissionService;
    @Mock private Authentication authentication;

    private ComponentController componentController;

    @BeforeEach
    void setUp() {
        componentController = new ComponentController(proxyService, permissionService);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_withAppCode_requiresViewerPermission() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn(new SecurityUtils.AuthDetails(2L, false));
        when(permissionService.hasAppPermission(2L, "app-a", "APP_VIEWER")).thenReturn(true);
        when(proxyService.getFromExecutor(eq("app-a"), anyString(), anyString()))
                .thenReturn("{\"records\":[],\"total\":0}");

        componentController.list("app-a", null, null, null, null, 1, 10);

        verify(permissionService).hasAppPermission(2L, "app-a", "APP_VIEWER");
    }

    @Test
    void list_withoutAppCode_returnsEmpty() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);

        String result = componentController.list(null, null, null, null, null, 1, 10);

        assertThat(result).contains("\"total\":0");
        verify(proxyService, never()).getFromExecutor(anyString(), anyString(), anyString());
    }

    @Test
    void list_withBlankAppCode_returnsEmpty() {
        String result = componentController.list("", null, null, null, null, 1, 10);

        assertThat(result).contains("\"total\":0");
        verify(proxyService, never()).getFromExecutor(anyString(), anyString(), anyString());
    }

    @Test
    void list_withoutPermission_throws() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn(new SecurityUtils.AuthDetails(2L, false));
        when(permissionService.hasAppPermission(2L, "app-a", "APP_VIEWER")).thenReturn(false);

        assertThatThrownBy(() -> componentController.list("app-a", null, null, null, null, 1, 10))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PERMISSION_DENIED);
    }

    @Test
    void stats_requiresViewerPermission() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn(new SecurityUtils.AuthDetails(2L, false));
        when(permissionService.hasAppPermission(2L, "app-a", "APP_VIEWER")).thenReturn(true);
        when(proxyService.resolveExecutorBaseUrl("app-a")).thenReturn("http://192.168.1.1:9999");
        when(proxyService.getDirectFromUrl(anyString(), anyString()))
                .thenReturn("{\"total\":10,\"records\":[{\"status\":1},{\"status\":1},{\"status\":0}]}");

        componentController.stats("app-a");

        verify(permissionService).hasAppPermission(2L, "app-a", "APP_VIEWER");
    }

    @Test
    void stats_superAdmin_bypassesPermission() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn(new SecurityUtils.AuthDetails(1L, true));
        when(proxyService.resolveExecutorBaseUrl("app-a")).thenReturn("http://192.168.1.1:9999");
        when(proxyService.getDirectFromUrl(anyString(), anyString()))
                .thenReturn("{\"total\":5,\"records\":[]}");

        String result = componentController.stats("app-a");

        assertThat(result).contains("\"total\":5");
        verify(permissionService, never()).hasAppPermission(anyLong(), anyString(), anyString());
    }

    @Test
    void stats_noExecutor_returnsZeroStats() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn(new SecurityUtils.AuthDetails(1L, true));
        when(proxyService.resolveExecutorBaseUrl("app-a")).thenReturn(null);

        String result = componentController.stats("app-a");

        assertThat(result).contains("\"total\":0");
        verify(proxyService, never()).getDirectFromUrl(anyString(), anyString());
    }

    @Test
    void list_unauthenticated_throws() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);

        assertThatThrownBy(() -> componentController.list("app-a", null, null, null, null, 1, 10))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }
}
