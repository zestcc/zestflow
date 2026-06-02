package com.zestflow.admin.config;

import com.zestflow.common.constant.RegistryAuthConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 机器间接口鉴权 — 注册/心跳/链同步等须携带 {@link RegistryAuthConstants#REGISTRY_TOKEN_HEADER}
 * <p>
 * 未配置 {@code zestflow.admin.registry-token} 时放行（仅适合本地开发）。
 */
@Slf4j
@Component
@Order(1)
public class RegistryTokenFilter extends OncePerRequestFilter {

    @Value("${zestflow.admin.registry-token:}")
    private String registryToken;

    private volatile boolean warned;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!requiresRegistryToken(request)) {
            chain.doFilter(request, response);
            return;
        }

        if (!StringUtils.hasText(registryToken)) {
            if (!warned) {
                warned = true;
                log.warn("zestflow.admin.registry-token 未配置，机器接口（registry/chains/sync）对公网开放，生产环境必须配置");
            }
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(RegistryAuthConstants.REGISTRY_TOKEN_HEADER);
        if (!registryToken.equals(header)) {
            log.warn("Registry Token 校验失败 uri={}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Invalid registry token\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean requiresRegistryToken(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        if (HttpMethod.POST.matches(method) && "/api/chains/sync".equals(uri)) {
            return true;
        }
        if (!uri.startsWith("/api/registry/")) {
            return false;
        }
        return HttpMethod.POST.matches(method) || HttpMethod.DELETE.matches(method);
    }
}
