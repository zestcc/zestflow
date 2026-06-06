package com.zestflow.admin.config;

import com.zestflow.common.constant.AdminApiPaths;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final TenantIpFilter tenantIpFilter;
    private final LoginRateLimitFilter loginRateLimitFilter;
    private final RegistryTokenFilter registryTokenFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
                .xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                .contentTypeOptions(contentType -> contentType.disable()) // 禁用后后端返回 JSON 不再强制 MIME 嗅探为下载
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 用户认证相关（登录/注册/找回密码等）
                .requestMatchers(AdminApiPaths.of("/auth/**"), "/uploads/**").permitAll()
                .requestMatchers(AdminApiPaths.of("/public/**")).permitAll()
                // 机器间通信：Executor/Collector 注册注销（后续可改为 Registry Token）
                .requestMatchers(HttpMethod.POST, AdminApiPaths.of("/registry/**")).permitAll()
                .requestMatchers(HttpMethod.DELETE, AdminApiPaths.of("/registry/**")).permitAll()
                // Executor 上报链加载状态（机器回调，非用户接口）
                .requestMatchers(HttpMethod.POST, AdminApiPaths.of("/chains/sync")).permitAll()
                // 其余 Admin API（含 Playground）须 JWT 认证 + Controller 内应用级 RBAC
                .requestMatchers(AdminApiPaths.of("/**")).authenticated()
                // 静态资源 + SPA 路由（前端自己控制登录态）
                .anyRequest().permitAll()
            )
            .addFilterBefore(registryTokenFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(tenantIpFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 仅挂在 Security 链上，避免与 Servlet 容器重复注册 TenantIpFilter */
    @Bean
    public FilterRegistrationBean<TenantIpFilter> tenantIpFilterServletRegistration(TenantIpFilter filter) {
        FilterRegistrationBean<TenantIpFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
