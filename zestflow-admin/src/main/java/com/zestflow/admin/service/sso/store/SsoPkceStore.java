package com.zestflow.admin.service.sso.store;

/**
 * PKCE state 与 code_verifier 临时存储（单机内存 / 集群 Redis）。
 */
public interface SsoPkceStore {

    void save(String state, String codeVerifier);

    /** 读取并删除，不存在或已过期返回 null */
    String consume(String state);
}
