package com.zestflow.admin.demo;

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
 * 每场景每 IP 滑动窗口限流，1 分钟窗口。
 * 每小时清理过期条目防止内存泄漏。
 */
@Slf4j
public class DemoRateLimiter {

    private final int defaultMaxRequests;
    private final ConcurrentHashMap<String, Deque<Long>> records = new ConcurrentHashMap<>();

    public DemoRateLimiter(int defaultMaxRequests) {
        this.defaultMaxRequests = defaultMaxRequests;
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "demo-limiter-cleaner");
            t.setDaemon(true);
            return t;
        }).scheduleAtFixedRate(this::cleanup, 60, 60, TimeUnit.MINUTES);
    }

    /**
     * 尝试获取许可，true=放行 false=被限
     *
     * @param sceneId   场景标识
     * @param maxPerMin 该场景每 IP 每分钟最大请求数
     */
    public boolean tryAcquire(String sceneId, int maxPerMin) {
        String ip = resolveRemoteIp();
        if (ip == null) return true;

        String key = sceneId + ":" + ip;
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000;

        Deque<Long> deque = records.computeIfAbsent(key, k -> new ArrayDeque<>());

        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst() < windowStart) {
                deque.pollFirst();
            }
            if (deque.size() >= maxPerMin) {
                log.warn("演示限流触发 scene={} ip={} limit={}", sceneId, ip, maxPerMin);
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
