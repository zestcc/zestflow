package com.zestflow.common.protocol;

/**
 * 链 HTTP 失败策略
 * <ul>
 *   <li>{@link #PROPAGATE} — 抛出 {@link com.zestflow.common.exception.ChainExecutionException}，由 Spring Advice 处理（Mode 3 默认）</li>
 *   <li>{@link #ERROR_HANDLER} — 调用链配置的 errorHandler 元件生成错误响应</li>
 *   <li>{@link #WRAPPED} — 返回 {@code {success:false,...}} 包装（兼容旧客户端 / DETAIL 模式）</li>
 * </ul>
 */
public enum ChainFailurePolicy {
    PROPAGATE,
    ERROR_HANDLER,
    WRAPPED
}
