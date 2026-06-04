package com.zestflow.admin.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.model.entity.TenantIpMappingPO;
import com.zestflow.admin.repository.TenantIpMappingMapper;
import com.zestflow.admin.tenant.TenantProvisioner;
import com.zestflow.admin.util.SecurityUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * IP → 租户自动映射（零门槛试玩）。
 * <p>
 * 无 JWT 时根据 IP 解析租户；无映射则调用 {@link TenantProvisioner#resolveOrProvisionByIp(String)}。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class TenantIpFilter extends OncePerRequestFilter {

    private final TenantIpMappingMapper tenantIpMappingMapper;
    private final TenantModeConfig tenantModeConfig;
    private final TenantProvisioner tenantProvisioner;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!"enabled".equals(tenantModeConfig.getIpDemoMode())) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/api/auth/")) {
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
            TenantIpMappingPO mapping = resolveMapping(clientIp);
            if (mapping != null) {
                applyIpDemoSession(mapping);
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private TenantIpMappingPO resolveMapping(String clientIp) {
        TenantIpMappingPO mapping = tenantIpMappingMapper.selectOne(
                new LambdaQueryWrapper<TenantIpMappingPO>()
                        .eq(TenantIpMappingPO::getIpAddress, clientIp)
                        .last("LIMIT 1")
        );
        if (mapping != null) {
            return mapping;
        }
        if (!"multi".equals(tenantModeConfig.getMode())) {
            return null;
        }
        return tenantProvisioner.resolveOrProvisionByIp(clientIp);
    }

    private void applyIpDemoSession(TenantIpMappingPO mapping) {
        TenantContextHolder.setTenantId(mapping.getTenantId());
        mapping.setLastActiveAt(LocalDateTime.now());
        tenantIpMappingMapper.updateById(mapping);
        tenantProvisioner.touchTenantActivity(mapping.getTenantId());

        UsernamePasswordAuthenticationToken ipAuth =
                new UsernamePasswordAuthenticationToken("ip-demo", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        ipAuth.setDetails(new SecurityUtils.AuthDetails(null, false, mapping.getTenantId()));
        SecurityContextHolder.getContext().setAuthentication(ipAuth);

        log.debug("IP 租户映射 ip={} tenantId={}", mapping.getIpAddress(), mapping.getTenantId());
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) return xf.split(",")[0].trim();
        String xr = request.getHeader("X-Real-IP");
        if (xr != null && !xr.isBlank()) return xr;
        return request.getRemoteAddr();
    }
}
