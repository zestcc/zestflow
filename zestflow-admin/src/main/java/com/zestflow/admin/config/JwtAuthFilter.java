package com.zestflow.admin.config;

import com.zestflow.admin.util.JwtUtils;
import com.zestflow.admin.util.SecurityUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        try {
            if (StringUtils.hasText(token) && jwtUtils.validateToken(token)) {
                Long userId = jwtUtils.getUserId(token);
                String username = jwtUtils.getUsername(token);
                boolean isSuperAdmin = jwtUtils.isSuperAdmin(token);
                Long currentTenantId = jwtUtils.getCurrentTenantId(token);

                TenantContextHolder.setTenantId(currentTenantId);

                SecurityUtils.AuthDetails details = new SecurityUtils.AuthDetails(userId, isSuperAdmin, currentTenantId);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, null);
                authentication.setDetails(details);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
