package com.zestflow.admin.schedule.platform;

/**
 * 平台内置任务键 — 全局唯一，写入 schedule.job_key。
 */
public final class PlatformJobKeys {

    /** Admin：业务 Cron 扫描轮询 */
    public static final String SCHEDULE_SCAN = "admin.schedule.scan";
    /** Admin：执行器/采集器离线检测 */
    public static final String OFFLINE_CHECK = "admin.registry.offline-check";
    /** Admin：过期异常注册记录清理 */
    public static final String OFFLINE_CLEANUP = "admin.registry.abnormal-cleanup";
    /** Admin：试玩租户与孤儿 IP 映射清理 */
    public static final String TENANT_CLEANUP = "admin.tenant.cleanup";
    /** Admin：链同步状态缓存淘汰 */
    public static final String CHAIN_SYNC_CACHE_EVICT = "admin.chain-sync.cache-evict";
    /** Admin：心跳时间异步刷库（UI 展示） */
    public static final String HEARTBEAT_DB_FLUSH = "admin.registry.heartbeat-flush";
    /** Admin：多执行器链版本漂移对账 */
    public static final String CHAIN_DRIFT_RECONCILE = "admin.reconcile.chain-drift";

    /** Executor 节点本地：链热加载轮询（元数据） */
    public static final String EXECUTOR_CHAIN_RELOAD = "executor.chain.reload";
    /** Executor 节点本地：注册心跳（元数据） */
    public static final String EXECUTOR_HEARTBEAT = "executor.registry.heartbeat";
    /** Collector 节点本地：注册心跳（元数据） */
    public static final String COLLECTOR_HEARTBEAT = "collector.registry.heartbeat";

    private PlatformJobKeys() {}
}
