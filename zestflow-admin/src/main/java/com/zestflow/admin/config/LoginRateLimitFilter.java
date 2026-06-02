package com.zestflow.admin.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 登录接口限流过滤器 — 基于 IP 维度的防爆破保护
 * <p>
 * 阈值：每分钟 20 次。超过返回 429，不阻塞其他请求。
 */
@Slf4j
@Component
@Order(0)
public class LoginRateLimitFilter extends OncePerRequestFilter {

    /** 每分钟最大请求数 */
    private static final int MAX_ATTEMPTS = 20;

    /** 限流缓存：IP → 请求计数 */
    private final Cache<String, Integer> attemptCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // 只拦截登录接口
        if (!"/api/auth/login".equals(path) || !"POST".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);
        Integer count = attemptCache.getIfPresent(clientIp);
        if (count != null && count >= MAX_ATTEMPTS) {
            log.warn("登录限流触发 clientIp={} count={}", clientIp, count);
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请 1 分钟后再试\"}");
            return;
        }

        attemptCache.put(clientIp, (count != null ? count : 0) + 1);
        chain.doFilter(request, response);
    }

    private static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        } else {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
