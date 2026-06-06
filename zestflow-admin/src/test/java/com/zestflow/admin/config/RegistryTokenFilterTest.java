package com.zestflow.admin.config;

import com.zestflow.common.constant.AdminApiPaths;
import com.zestflow.common.constant.RegistryAuthConstants;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RegistryTokenFilterTest {

    @Mock
    private FilterChain filterChain;

    private RegistryTokenFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new RegistryTokenFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    void rejectsMissingTokenWhenConfigured() throws Exception {
        ReflectionTestUtils.setField(filter, "registryToken", "secret");
        request.setMethod("POST");
        request.setRequestURI(AdminApiPaths.of("/registry/executor/heartbeat"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void acceptsValidToken() throws Exception {
        ReflectionTestUtils.setField(filter, "registryToken", "secret");
        request.setMethod("POST");
        request.setRequestURI(AdminApiPaths.of("/registry/executor/heartbeat"));
        request.addHeader(RegistryAuthConstants.REGISTRY_TOKEN_HEADER, "secret");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void allowsOpenModeWhenTokenNotConfigured() throws Exception {
        ReflectionTestUtils.setField(filter, "registryToken", "");
        request.setMethod("POST");
        request.setRequestURI(AdminApiPaths.of("/registry/executor/heartbeat"));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
