package com.zestflow.common.model.event;

/**
 * 链事件类型枚举
 */
public enum ChainEventType {

    /* ====== 管理事件 ====== */
    CHAIN_LOADED,
    CHAIN_UNLOADED,
    CHAIN_RELOADED,

    /* ====== 执行事件 ====== */
    CHAIN_STARTED,
    CHAIN_SUCCESS,
    CHAIN_FAILED,
    CHAIN_TIMEOUT,
    CHAIN_STOPPED,
    CHAIN_COMPENSATED,

    /* ====== 节点事件 ====== */
    NODE_STARTED,
    NODE_SUCCESS,
    NODE_FAILED,
    NODE_RETRYING,
    NODE_RETRY_EXHAUSTED,
    NODE_TIMEOUT,
    NODE_FALLBACK_START,
    NODE_FALLBACK_SUCCESS,
    NODE_FALLBACK_FAILED,
    NODE_COMPENSATING,
    NODE_COMPENSATED
}
