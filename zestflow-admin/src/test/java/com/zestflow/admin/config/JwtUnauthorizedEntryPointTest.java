package com.zestflow.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.common.constant.AdminApiPaths;
import com.zestflow.common.model.Result;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class JwtUnauthorizedEntryPointTest {

    @Mock
    private BadCredentialsException authException;

    private final JwtUnauthorizedEntryPoint entryPoint =
            new JwtUnauthorizedEntryPoint(new ObjectMapper());

    @Test
    void commence_writes401WithAuthUnauthorized() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        entryPoint.commence(new MockHttpServletRequest(), response, authException);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        Result<?> body = new ObjectMapper().readValue(response.getContentAsString(), Result.class);
        assertThat(body.getCode()).isEqualTo(401);
        assertThat(body.getErrorCode()).isEqualTo("AUTH_UNAUTHORIZED");
    }
}
