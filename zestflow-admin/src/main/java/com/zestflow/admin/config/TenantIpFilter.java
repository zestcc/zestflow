package com.zestflow.admin.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.model.entity.TenantIpMappingPO;
import com.zestflow.admin.repository.TenantIpMappingMapper;
import com.zestflow.admin.util.SecurityUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * IP → 租户自动映射过滤器（演示环境）
 * <p>
 * 在 JwtAuthFilter 之前执行。对于没有 JWT 的请求，根据客户端 IP 从
 * tenant_ip_mapping 表查找对应的租户，设置 TenantContextHolder 和匿名认证。
 * 有 JWT 的请求跳过此过滤器（由 JwtAuthFilter 处理）。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class TenantIpFilter extends OncePerRequestFilter {

    private final TenantIpMappingMapper tenantIpMappingMapper;
    private final TenantModeConfig tenantModeConfig;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!"enabled".equals(tenantModeConfig.getIpDemoMode())) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);
        if (clientIp == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            TenantIpMappingPO mapping = tenantIpMappingMapper.selectOne(
                    new LambdaQueryWrapper<TenantIpMappingPO>()
                            .eq(TenantIpMappingPO::getIpAddress, clientIp)
                            .last("LIMIT 1")
            );

            if (mapping != null) {
                TenantContextHolder.setTenantId(mapping.getTenantId());
                mapping.setLastActiveAt(LocalDateTime.now());
                tenantIpMappingMapper.updateById(mapping);

                AnonymousAuthenticationToken anonymousAuth =
                        new AnonymousAuthenticationToken("anonymous", "anonymous",
                                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
                anonymousAuth.setDetails(new SecurityUtils.AuthDetails(null, false, mapping.getTenantId()));
                SecurityContextHolder.getContext().setAuthentication(anonymousAuth);

                log.debug("IP 租户映射 ip={} tenantId={}", clientIp, mapping.getTenantId());
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) return xf.split(",")[0].trim();
        String xr = request.getHeader("X-Real-IP");
        if (xr != null && !xr.isBlank()) return xr;
        return request.getRemoteAddr();
    }
}
