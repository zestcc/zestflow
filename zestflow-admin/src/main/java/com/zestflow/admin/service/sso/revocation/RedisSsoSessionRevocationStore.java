package com.zestflow.admin.service.sso.revocation;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 集群吊销存储 — cluster + Redis 时使用。
 */
@RequiredArgsConstructor
public class RedisSsoSessionRevocationStore implements SsoSessionRevocationStore {

    private static final String PREFIX = "zestflow:admin:sso-logout:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    @Override
    public void revokeByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return;
        }
        redisTemplate.opsForValue().set(PREFIX + username.trim(), "1", ttl);
    }

    @Override
    public void clearRevocation(String username) {
        if (!StringUtils.hasText(username)) {
            return;
        }
        redisTemplate.delete(PREFIX + username.trim());
    }

    @Override
    public boolean isRevoked(String username) {
        if (!StringUtils.hasText(username)) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + username.trim()));
    }
}
