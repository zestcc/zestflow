package com.zestflow.admin.playground.support;

import com.zestflow.admin.config.TenantModeConfig;
import com.zestflow.admin.service.PermissionService;
import com.zestflow.admin.util.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaygroundAccessControlTest {

    @Mock private PermissionService permissionService;
    @Mock private TenantModeConfig tenantModeConfig;
    @InjectMocks private PlaygroundAccessControl accessControl;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void isIpDemoTenantSession_trueWhenAnonymousWithTenant() {
        when(tenantModeConfig.getIpDemoMode()).thenReturn("enabled");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "ip-demo", null, java.util.List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        auth.setDetails(new SecurityUtils.AuthDetails(null, false, 2L));
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(accessControl.isIpDemoTenantSession()).isTrue();
    }

    @Test
    void isIpDemoTenantSession_falseWhenDisabled() {
        when(tenantModeConfig.getIpDemoMode()).thenReturn("disabled");
        assertThat(accessControl.isIpDemoTenantSession()).isFalse();
    }
}
