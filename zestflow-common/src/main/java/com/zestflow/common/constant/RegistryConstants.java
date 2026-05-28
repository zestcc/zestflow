package com.zestflow.common.constant;

public final class RegistryConstants {

    private RegistryConstants() {}

    /** 默认心跳间隔（秒） */
    public static final int DEFAULT_HEARTBEAT_INTERVAL_SECONDS = 30;

    /** 默认离线判定倍数：心跳间隔 * 倍数 = 死亡超时 */
    public static final int DEAD_TIMEOUT_MULTIPLIER = 3;

    /** 执行器在线状态：1-在线 0-主动下线 2-异常离线 */
    public static final int STATUS_ONLINE = 1;
    public static final int STATUS_OFFLINE = 0;
    public static final int STATUS_ABNORMAL = 2;

    /** 默认注册超时（毫秒） */
    public static final int DEFAULT_REGISTRY_TIMEOUT_MS = 5000;

    /** 默认心跳超时（毫秒） */
    public static final int DEFAULT_HEARTBEAT_TIMEOUT_MS = 5000;
}
