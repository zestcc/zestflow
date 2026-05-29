package com.zestflow.executor.circuit;

/**
 * 熔断器状态枚举
 */
public enum CircuitState {

    /** 闭合：正常状态，请求放行 */
    CLOSED,
    /** 断开：熔断状态，请求直接拒绝 */
    OPEN,
    /** 半开：试探恢复状态，允许少量请求 */
    HALF_OPEN
}
