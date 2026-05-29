package com.zestflow.executor.interceptor;

import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 鉴权拦截器 — 校验访问令牌
 * <p>
 * 从链上下文的 headers 中提取 accessToken 与本地配置比对。
 * 适用于内部网络白名单场景，不做复杂 RBAC。
 */
@Slf4j
public class AuthInterceptor implements ChainInterceptor {

    private final String expectedToken;

    public AuthInterceptor(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    public void beforeChain(String chainCode, ChainContext ctx) {
        if (expectedToken == null || expectedToken.isEmpty()) {
            return; // 未配置 token，跳过鉴权
        }

        String token = ctx.getHeader("accessToken", String.class);
        if (token == null || !expectedToken.equals(token)) {
            throw new SecurityException("accessToken 校验失败 chainCode=" + chainCode);
        }
    }

    @Override
    public void afterChain(String chainCode, ChainContext ctx, List<?> nodeResults) {
        // 不做操作
    }

    @Override
    public void onChainError(String chainCode, ChainContext ctx, Throwable e) {
        // 不做操作
    }

    @Override
    public int order() {
        return Integer.MIN_VALUE; // 最先执行
    }
}
