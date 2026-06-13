package com.zestflow.admin.service.sso.revocation;

import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单机吊销存储 — standalone 默认实现。
 */
public class InMemorySsoSessionRevocationStore implements SsoSessionRevocationStore {

    private final Duration ttl;
    private final Map<String, Instant> revokedUntil = new ConcurrentHashMap<>();

    public InMemorySsoSessionRevocationStore(Duration ttl) {
        this.ttl = ttl;
    }

    @Override
    public void revokeByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return;
        }
        revokedUntil.put(username.trim(), Instant.now().plus(ttl));
    }

    @Override
    public void clearRevocation(String username) {
        if (!StringUtils.hasText(username)) {
            return;
        }
        revokedUntil.remove(username.trim());
    }

    @Override
    public boolean isRevoked(String username) {
        if (!StringUtils.hasText(username)) {
            return false;
        }
        Instant until = revokedUntil.get(username.trim());
        if (until == null) {
            return false;
        }
        if (until.isBefore(Instant.now())) {
            revokedUntil.remove(username.trim());
            return false;
        }
        return true;
    }
}
