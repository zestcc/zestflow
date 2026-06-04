package com.zestflow.admin.config;

import com.zestflow.admin.model.entity.TenantIpMappingPO;
import com.zestflow.admin.repository.TenantIpMappingMapper;
import com.zestflow.admin.tenant.TenantProvisioner;
import com.zestflow.admin.util.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.FilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantIpFilterTest {

    @Mock private TenantIpMappingMapper tenantIpMappingMapper;
    @Mock private TenantModeConfig tenantModeConfig;
    @Mock private TenantProvisioner tenantProvisioner;
    @Mock private FilterChain filterChain;

    private TenantIpFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TenantIpFilter(tenantIpMappingMapper, tenantModeConfig, tenantProvisioner);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    void disabled_skipsFilter() throws Exception {
        when(tenantModeConfig.getIpDemoMode()).thenReturn("disabled");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        verify(tenantIpMappingMapper, never()).selectOne(any());
        verify(tenantProvisioner, never()).resolveOrProvisionByIp(any());
    }

    @Test
    void enabled_skipsWhenBearerPresent() throws Exception {
        when(tenantModeConfig.getIpDemoMode()).thenReturn("enabled");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        request.setRemoteAddr("127.0.0.1");

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        verify(tenantIpMappingMapper, never()).selectOne(any());
    }

    @Test
    void enabled_skipsAuthPath() throws Exception {
        when(tenantModeConfig.getIpDemoMode()).thenReturn("enabled");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/auth/login");
        request.setRemoteAddr("127.0.0.1");

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        verify(tenantIpMappingMapper, never()).selectOne(any());
        verify(tenantProvisioner, never()).resolveOrProvisionByIp(any());
    }

    @Test
    void enabled_provisionsWhenNoMappingAndMultiMode() throws Exception {
        when(tenantModeConfig.getIpDemoMode()).thenReturn("enabled");
        when(tenantModeConfig.getMode()).thenReturn("multi");

        TenantIpMappingPO created = new TenantIpMappingPO();
        created.setTenantId(77L);
        created.setIpAddress("10.0.0.200");
        when(tenantIpMappingMapper.selectOne(any())).thenReturn(null);
        when(tenantProvisioner.resolveOrProvisionByIp("10.0.0.200")).thenReturn(created);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "10.0.0.200");

        doAnswer(inv -> {
            assertThat(com.zestflow.admin.config.TenantContextHolder.getTenantId()).isEqualTo(77L);
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        verify(tenantProvisioner).resolveOrProvisionByIp("10.0.0.200");
        verify(tenantIpMappingMapper).updateById(created);
        verify(tenantProvisioner).touchTenantActivity(77L);
    }

    @Test
    void enabled_appliesExistingMapping() throws Exception {
        when(tenantModeConfig.getIpDemoMode()).thenReturn("enabled");

        TenantIpMappingPO existing = new TenantIpMappingPO();
        existing.setTenantId(2L);
        existing.setIpAddress("10.0.0.101");
        when(tenantIpMappingMapper.selectOne(any())).thenReturn(existing);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "10.0.0.101");

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        verify(tenantProvisioner, never()).resolveOrProvisionByIp(any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getDetails())
                .isInstanceOf(SecurityUtils.AuthDetails.class);
    }
}
