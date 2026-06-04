package com.zestflow.common.model.registry;

/**
 * Admin 注册中心心跳调用结果 — 供 Executor / Collector 客户端区分失败类型。
 */
public enum HeartbeatResult {

    /** 心跳成功 */
    OK,

    /** 实例未在 Admin 注册（应走 register） */
    NOT_REGISTERED,

    /** 网络/5xx 等瞬时失败（应重试心跳，勿立即 register） */
    TRANSIENT_FAILURE
}
