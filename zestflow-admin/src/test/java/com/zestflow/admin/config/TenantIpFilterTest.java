package com.zestflow.admin.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.demo.ip.DemoTenantProvisioner;
import com.zestflow.admin.model.entity.TenantIpMappingPO;
import com.zestflow.admin.repository.TenantIpMappingMapper;
import com.zestflow.admin.util.SecurityUtils;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantIpFilterTest {

    @Mock private TenantIpMappingMapper tenantIpMappingMapper;
    @Mock private TenantModeConfig tenantModeConfig;
    @Mock private ObjectProvider<DemoTenantProvisioner> demoTenantProvisioner;
    @Mock private FilterChain filterChain;

    private TenantIpFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TenantIpFilter(tenantIpMappingMapper, tenantModeConfig, demoTenantProvisioner);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    void disabled_skipsMapping() throws Exception {
        when(tenantModeConfig.getIpDemoMode()).thenReturn("disabled");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "10.0.0.101");

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        verify(tenantIpMappingMapper, never()).selectOne(any());
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void enabled_mapsIpToTenantDuringChain() throws Exception {
        when(tenantModeConfig.getIpDemoMode()).thenReturn("enabled");
        TenantIpMappingPO mapping = new TenantIpMappingPO();
        mapping.setTenantId(2L);
        mapping.setIpAddress("10.0.0.101");
        when(tenantIpMappingMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mapping);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "10.0.0.101");

        doAnswer(inv -> {
            assertThat(TenantContextHolder.getTenantId()).isEqualTo(2L);
            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth).isNotNull();
            assertThat(auth.isAuthenticated()).isTrue();
            assertThat(auth.getDetails()).isInstanceOf(SecurityUtils.AuthDetails.class);
            assertThat(((SecurityUtils.AuthDetails) auth.getDetails()).currentTenantId()).isEqualTo(2L);
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        verify(tenantIpMappingMapper).updateById(mapping);
    }

    @Test
    void enabled_withBearer_skipsIpLookup() throws Exception {
        when(tenantModeConfig.getIpDemoMode()).thenReturn("enabled");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer fake.jwt.token");
        request.addHeader("X-Forwarded-For", "10.0.0.101");

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
        verify(demoTenantProvisioner, never()).getIfAvailable();
    }

    @Test
    void enabled_provisionsWhenNoMappingAndMultiMode() throws Exception {
        when(tenantModeConfig.getIpDemoMode()).thenReturn("enabled");
        when(tenantModeConfig.getMode()).thenReturn("multi");
        DemoTenantProvisioner provisionerBean = mock(DemoTenantProvisioner.class);
        when(demoTenantProvisioner.getIfAvailable()).thenReturn(provisionerBean);

        TenantIpMappingPO created = new TenantIpMappingPO();
        created.setTenantId(77L);
        created.setIpAddress("10.0.0.200");
        when(tenantIpMappingMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(provisionerBean.resolveOrProvision("10.0.0.200")).thenReturn(created);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "10.0.0.200");

        doAnswer(inv -> {
            assertThat(TenantContextHolder.getTenantId()).isEqualTo(77L);
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        verify(provisionerBean).resolveOrProvision("10.0.0.200");
        verify(tenantIpMappingMapper).updateById(created);
    }

}
