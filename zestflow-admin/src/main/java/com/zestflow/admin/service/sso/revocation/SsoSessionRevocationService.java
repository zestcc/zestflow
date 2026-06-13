package com.zestflow.admin.service.sso.revocation;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * SSO Back-Channel 登出后吊销本地 JWT（按用户名，与 JwtAuthFilter principal 一致）。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "zestflow.sso", name = "enabled", havingValue = "true")
public class SsoSessionRevocationService {

    private final SsoSessionRevocationStore store;

    public void revokeByUsername(String username) {
        store.revokeByUsername(username);
    }

    public void clearRevocation(String username) {
        store.clearRevocation(username);
    }

    public boolean isRevoked(String username) {
        return store.isRevoked(username);
    }
}
