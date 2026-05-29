package com.zestflow.executor.interceptor;

import com.zestflow.executor.context.ChainContext;

import java.util.List;

/**
 * 链级拦截器接口
 * <p>
 * 在链执行前/后/异常时触发，用于日志、指标、鉴权等横切关注点。
 * 实现类通过 SPI / Spring 自动注入。
 */
public interface ChainInterceptor {

    /**
     * 链执行前
     */
    void beforeChain(String chainCode, ChainContext ctx);

    /**
     * 链执行后
     */
    void afterChain(String chainCode, ChainContext ctx, List<?> nodeResults);

    /**
     * 链异常
     */
    void onChainError(String chainCode, ChainContext ctx, Throwable e);

    /**
     * 排序（数字越小越先执行）
     */
    default int order() {
        return 0;
    }
}
