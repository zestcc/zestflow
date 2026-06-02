package com.zestflow.common.constant;

/**
 * Admin 与 Executor/Collector 机器间通信鉴权常量
 */
public final class RegistryAuthConstants {

    private RegistryAuthConstants() {
    }

    /** 注册/心跳/链同步等机器接口的请求头 */
    public static final String REGISTRY_TOKEN_HEADER = "X-Registry-Token";
}
