package com.zestflow.admin.config;

import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 登录频率限制 — 简单内存实现
 * <p>
 * 同一 IP 5 分钟内失败 10 次则临时锁定 5 分钟。
 * 生产环境建议替换为 Redis 实现。
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 10;
    private static final long WINDOW_MS = 5 * 60 * 1000L;
    private static final long LOCK_DURATION_MS = 5 * 60 * 1000L;

    private final Map<String, AttemptRecord> attempts = new ConcurrentHashMap<>();

    public void check(String ip) {
        AttemptRecord record = attempts.get(ip);
        if (record == null) return;

        if (record.isLocked() && System.currentTimeMillis() - record.lockStart < LOCK_DURATION_MS) {
            throw new BizException(ErrorCode.LOGIN_RATE_LIMITED);
        }

        if (record.isLocked()) {
            attempts.remove(ip);
        }
    }

    public void recordFailure(String ip) {
        AttemptRecord record = attempts.computeIfAbsent(ip, k -> new AttemptRecord());
        record.increment();

        if (record.count.get() >= MAX_ATTEMPTS) {
            record.lock();
        }
    }

    public void reset(String ip) {
        attempts.remove(ip);
    }

    private static class AttemptRecord {
        final long windowStart = System.currentTimeMillis();
        final AtomicInteger count = new AtomicInteger(0);
        boolean locked;
        long lockStart;

        void increment() {
            if (System.currentTimeMillis() - windowStart > WINDOW_MS) {
                count.set(0);
            }
            count.incrementAndGet();
        }

        boolean isLocked() {
            return locked && System.currentTimeMillis() - lockStart < LOCK_DURATION_MS;
        }

        void lock() {
            locked = true;
            lockStart = System.currentTimeMillis();
        }
    }
}
