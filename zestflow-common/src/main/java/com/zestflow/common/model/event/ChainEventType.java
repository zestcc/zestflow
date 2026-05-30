package com.zestflow.common.model.event;

/**
 * 链事件类型枚举
 * <p>
 * 覆盖管理事件、执行事件、节点事件、发布事件、设计变更事件，
 * 统一系统内所有事件类型的定义。
 */
public enum ChainEventType {

    /* ====== 管理事件（链加载/卸载） ====== */
    CHAIN_LOADED,
    CHAIN_UNLOADED,
    CHAIN_RELOADED,

    /* ====== 执行事件（链级生命周期） ====== */
    CHAIN_STARTED,
    CHAIN_SUCCESS,
    CHAIN_FAILED,
    CHAIN_TIMEOUT,
    CHAIN_STOPPED,
    CHAIN_COMPENSATED,

    /* ====== 节点事件（节点级生命周期） ====== */
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
    NODE_COMPENSATED,

    /* ====== 发布事件（链部署/热切换） ====== */
    PUBLISH_REQUESTED,
    PUBLISH_EXECUTOR_STARTED,
    PUBLISH_EXECUTOR_COMPLETED,
    PUBLISH_EXECUTOR_FAILED,
    PUBLISH_COMPLETED,
    PUBLISH_PARTIAL,
    PUBLISH_ROLLBACK,
    PUBLISH_ROLLBACK_COMPLETED,

    /* ====== 设计变更事件 ====== */
    DESIGN_CREATED,
    DESIGN_UPDATED,
    DESIGN_DELETED,
    DESIGN_GRAPH_CHANGED,
    DESIGN_STATUS_CHANGED,

    /* ====== 链 CRUD 事件 ====== */
    CHAIN_CREATED,
    CHAIN_UPDATED,
    CHAIN_DELETED,
    CHAIN_STATUS_CHANGED,

    /* ====== 执行器生命周期事件 ====== */
    EXECUTOR_REGISTERED,
    EXECUTOR_DEREGISTERED,
    EXECUTOR_HEARTBEAT,
    EXECUTOR_ONLINE,
    EXECUTOR_OFFLINE,
    EXECUTOR_ABNORMAL
}
