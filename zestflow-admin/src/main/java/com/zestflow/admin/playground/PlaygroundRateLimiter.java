package com.zestflow.admin.playground;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * IP 级滑动窗口限流，1 分钟窗口。
 * 每小时清理过期条目防止内存泄漏。
 */
@Slf4j
public class PlaygroundRateLimiter {

    private final int maxRequests;
    private final ConcurrentHashMap<String, Deque<Long>> records = new ConcurrentHashMap<>();

    public PlaygroundRateLimiter(int maxRequests) {
        this.maxRequests = maxRequests;
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "playground-limiter-cleaner");
            t.setDaemon(true);
            return t;
        }).scheduleAtFixedRate(this::cleanup, 60, 60, TimeUnit.MINUTES);
    }

    /**
     * 尝试获取许可，true=放行 false=被限
     */
    public boolean tryAcquire() {
        String ip = resolveRemoteIp();
        if (ip == null) return true;

        long now = System.currentTimeMillis();
        long windowStart = now - 60_000;

        Deque<Long> deque = records.computeIfAbsent(ip, k -> new ArrayDeque<>());

        synchronized (deque) {
            // 移除窗口外记录
            while (!deque.isEmpty() && deque.peekFirst() < windowStart) {
                deque.pollFirst();
            }
            if (deque.size() >= maxRequests) {
                log.warn("Playground 限流触发 ip={} count={}", ip, deque.size());
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }

    private void cleanup() {
        long cutoff = System.currentTimeMillis() - 120_000;
        records.values().removeIf(deque -> {
            synchronized (deque) {
                while (!deque.isEmpty() && deque.peekFirst() < cutoff) {
                    deque.pollFirst();
                }
                return deque.isEmpty();
            }
        });
    }

    private static String resolveRemoteIp() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                HttpServletRequest req = sra.getRequest();
                String xff = req.getHeader("X-Forwarded-For");
                if (xff != null && !xff.isBlank() && !"unknown".equalsIgnoreCase(xff)) {
                    return xff.split(",")[0].trim();
                }
                return req.getRemoteAddr();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
