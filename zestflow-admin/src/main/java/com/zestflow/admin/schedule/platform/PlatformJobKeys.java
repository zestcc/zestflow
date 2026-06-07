package com.zestflow.admin.schedule.platform;

/**
 * 平台内置任务键 — 全局唯一，写入 schedule.job_key。
 */
public final class PlatformJobKeys {

    /** Admin：业务 Cron 扫描（已废弃 — 见 executor.schedule.embedded） */
    @Deprecated
    public static final String SCHEDULE_SCAN = "admin.schedule.scan";
    /** Executor：嵌入式链 Cron（业务库 zf_schedule） */
    public static final String EXECUTOR_SCHEDULE_EMBEDDED = "executor.schedule.embedded";
    /** @deprecated 心跳事件驱动同步 DB，不再定时刷库 */
    @Deprecated
    public static final String HEARTBEAT_DB_FLUSH = "admin.registry.heartbeat-flush";
    /** @deprecated 离线检测改为心跳过期事件驱动 */
    @Deprecated
    public static final String OFFLINE_CHECK = "admin.registry.offline-check";
    /** Admin：过期异常注册记录清理 */
    public static final String OFFLINE_CLEANUP = "admin.registry.abnormal-cleanup";
    /** Admin：试玩租户与孤儿 IP 映射清理 */
    public static final String TENANT_CLEANUP = "admin.tenant.cleanup";
    /** Admin：链同步状态缓存淘汰 */
    public static final String CHAIN_SYNC_CACHE_EVICT = "admin.chain-sync.cache-evict";
    /** Admin：多执行器链版本漂移对账 */
    public static final String CHAIN_DRIFT_RECONCILE = "admin.reconcile.chain-drift";
    /** Admin：执行 SLA 邮件告警（已下沉 Collector） */
    @Deprecated
    public static final String EXECUTION_SLA_ALERT = "admin.alert.execution-sla";
    /** Collector：执行 SLA 扫描 */
    public static final String COLLECTOR_SLA_ALERT = "collector.alert.execution-sla";

    /** Executor 节点本地：链热加载轮询（元数据） */
    public static final String EXECUTOR_CHAIN_RELOAD = "executor.chain.reload";
    /** Executor 节点本地：注册心跳（元数据） */
    public static final String EXECUTOR_HEARTBEAT = "executor.registry.heartbeat";
    /** Collector 节点本地：注册心跳（元数据） */
    public static final String COLLECTOR_HEARTBEAT = "collector.registry.heartbeat";

    private PlatformJobKeys() {}
}
