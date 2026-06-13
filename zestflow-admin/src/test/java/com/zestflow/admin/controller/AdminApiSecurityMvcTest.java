package com.zestflow.admin.controller;

import com.zestflow.admin.config.AdminApiWebConfig;
import com.zestflow.admin.config.JwtAuthFilter;
import com.zestflow.admin.config.JwtUnauthorizedEntryPoint;
import com.zestflow.admin.util.JwtUtils;
import com.zestflow.common.constant.AdminApiPaths;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc 验证受保护 Admin API：无 JWT → HTTP 401 + AUTH_UNAUTHORIZED（BB-01）。
 */
@WebMvcTest(controllers = DashboardController.class)
@Import({AdminApiWebConfig.class, JwtAuthFilter.class, AdminApiSecurityMvcTest.TestSecurityConfig.class})
class AdminApiSecurityMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private com.zestflow.admin.service.DashboardService dashboardService;

    @MockBean
    private JwtUtils jwtUtils;

    @Test
    void dashboardWithoutJwt_returns401() throws Exception {
        mockMvc.perform(get(AdminApiPaths.of("/dashboard/stats")))
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
                            .requestMatchers(AdminApiPaths.of("/**")).authenticated()
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
