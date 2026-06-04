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

    /**
     * 连续心跳失败多少次后才降级为重新注册（对标 xxl-job / Nacos 瞬时容错）。
     */
    public static final int HEARTBEAT_FAILURE_THRESHOLD_BEFORE_REREGISTER = 3;

    /** Admin 侧将内存心跳时间异步刷入 DB 的间隔（毫秒），仅供 UI 展示 */
    public static final long HEARTBEAT_DB_FLUSH_INTERVAL_MS = 300_000L;

    /** 离线判定窗口（毫秒）：心跳间隔 × 倍数 */
    public static long deadTimeoutMillis() {
        return DEFAULT_HEARTBEAT_INTERVAL_SECONDS * DEAD_TIMEOUT_MULTIPLIER * 1000L;
    }
}
