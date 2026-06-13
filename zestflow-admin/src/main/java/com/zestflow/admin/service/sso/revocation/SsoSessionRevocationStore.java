package com.zestflow.admin.service.sso.revocation;

/**
 * SSO Back-Channel 登出后的本地 JWT 吊销存储。
 */
public interface SsoSessionRevocationStore {

    void revokeByUsername(String username);

    void clearRevocation(String username);

    boolean isRevoked(String username);
}
