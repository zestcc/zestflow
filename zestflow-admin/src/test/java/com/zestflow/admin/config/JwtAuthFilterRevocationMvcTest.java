package com.zestflow.admin.config;

import com.zestflow.admin.controller.AuthController;
import com.zestflow.admin.service.TenantService;
import com.zestflow.admin.service.UserService;
import com.zestflow.admin.service.sso.SsoAuthService;
import com.zestflow.admin.service.sso.revocation.SsoSessionRevocationService;
import com.zestflow.admin.util.JwtUtils;
import com.zestflow.common.constant.AdminApiPaths;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import({AdminApiWebConfig.class, JwtAuthFilter.class, JwtAuthFilterRevocationMvcTest.TestSecurityConfig.class})
class JwtAuthFilterRevocationMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;
    @MockBean
    private LoginRateLimiter loginRateLimiter;
    @MockBean
    private TenantService tenantService;
    @MockBean
    private JwtUtils jwtUtils;
    @MockBean
    private SsoAuthService ssoAuthService;
    @MockBean
    private SsoSessionRevocationService sessionRevocationService;

    @BeforeEach
    void setUp() {
        when(jwtUtils.validateToken("revoked-token")).thenReturn(true);
        when(jwtUtils.getUsername("revoked-token")).thenReturn("admin");
        when(sessionRevocationService.isRevoked("admin")).thenReturn(true);
    }

    @Test
    void userinfoWithRevokedJwt_returns401() throws Exception {
        mockMvc.perform(get(AdminApiPaths.of("/auth/userinfo"))
                        .header("Authorization", "Bearer revoked-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.errorCode").value("AUTH_UNAUTHORIZED"));
    }

    @Configuration
    @EnableWebSecurity
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http,
                                                  JwtAuthFilter jwtAuthFilter,
                                                  JwtUnauthorizedEntryPoint entryPoint) throws Exception {
            http
                    .anonymous(anonymous -> anonymous.disable())
                    .csrf(csrf -> csrf.disable())
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint))
                    .authorizeHttpRequests(auth -> auth
                            .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                            .requestMatchers(HttpMethod.POST,
                                    AdminApiPaths.of("/auth/login"),
                                    AdminApiPaths.of("/auth/register"),
                                    AdminApiPaths.of("/auth/forgot"),
                                    AdminApiPaths.of("/auth/reset-password"),
                                    AdminApiPaths.of("/auth/logout")).permitAll()
                            .requestMatchers(HttpMethod.GET, AdminApiPaths.of("/auth/verify-email")).permitAll()
                            .requestMatchers(AdminApiPaths.of("/auth/sso/**")).permitAll()
                            .requestMatchers(
                                    AdminApiPaths.of("/auth/userinfo"),
                                    AdminApiPaths.of("/auth/profile"),
                                    AdminApiPaths.of("/auth/password"),
                                    AdminApiPaths.of("/auth/force-password"),
                                    AdminApiPaths.of("/auth/tenants"),
                                    AdminApiPaths.of("/auth/switch-tenant/**"),
                                    AdminApiPaths.of("/auth/avatar")).authenticated()
                            .anyRequest().permitAll())
                    .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
            return http.build();
        }

        @Bean
        JwtUnauthorizedEntryPoint jwtUnauthorizedEntryPoint(ObjectMapper objectMapper) {
            return new JwtUnauthorizedEntryPoint(objectMapper);
        }
    }
}
