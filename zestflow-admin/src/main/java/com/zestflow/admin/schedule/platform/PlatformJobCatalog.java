package com.zestflow.admin.schedule.platform;

import com.zestflow.common.constant.RegistryConstants;

import java.util.List;

/**
 * 平台内置任务目录 — 所有 Admin/Executor/Collector 定时任务必须在此登记，禁止黑盒 @Scheduled。
 */
public final class PlatformJobCatalog {

    private PlatformJobCatalog() {}

    public static List<PlatformJobDefinition> all() {
        return List.of(
                PlatformJobDefinition.builder()
                        .jobKey(PlatformJobKeys.EXECUTOR_SCHEDULE_EMBEDDED)
                        .name("嵌入式链 Cron")
                        .module("executor")
                        .scheduleKind(ScheduleKind.FIXED_DELAY)
                        .fixedIntervalMs(15_000L)
                        .remark("Executor 读业务库 zf_schedule，本地 Cron + 分片触发（Admin 不参与运行时）")
                        .editable(false)
                        .remote(true)
                        .build(),
                PlatformJobDefinition.builder()
                        .jobKey(PlatformJobKeys.OFFLINE_CLEANUP)
                        .name("异常注册记录清理")
                        .module("admin")
                        .scheduleKind(ScheduleKind.FIXED_RATE)
                        .fixedIntervalMs(1_800_000L)
                        .remark("每 30 分钟清理超过 24h 未恢复的异常离线记录")
                        .editable(false)
                        .remote(false)
                        .build(),
                PlatformJobDefinition.builder()
                        .jobKey(PlatformJobKeys.TENANT_CLEANUP)
                        .name("试玩租户回收")
                        .module("admin")
                        .scheduleKind(ScheduleKind.FIXED_RATE)
                        .fixedIntervalMs(300_000L)
                        .remark("清理过期试玩租户与孤儿 IP 映射")
                        .editable(false)
                        .remote(false)
                        .build(),
                PlatformJobDefinition.builder()
                        .jobKey(PlatformJobKeys.CHAIN_SYNC_CACHE_EVICT)
                        .name("链同步缓存淘汰")
                        .module("admin")
                        .scheduleKind(ScheduleKind.FIXED_RATE)
                        .fixedIntervalMs(60_000L)
                        .remark("淘汰过期的链同步状态缓存")
                        .editable(false)
                        .remote(false)
                        .build(),
                PlatformJobDefinition.builder()
                        .jobKey(PlatformJobKeys.CHAIN_DRIFT_RECONCILE)
                        .name("执行器链版本对账")
                        .module("admin")
                        .scheduleKind(ScheduleKind.FIXED_DELAY)
                        .fixedIntervalMs(120_000L)
                        .remark("比对多执行器 active-codes，发现链版本漂移")
                        .editable(false)
                        .remote(false)
                        .build(),
                PlatformJobDefinition.builder()
                        .jobKey(PlatformJobKeys.COLLECTOR_SLA_ALERT)
                        .name("执行 SLA 邮件告警")
                        .module("collector")
                        .scheduleKind(ScheduleKind.FIXED_RATE)
                        .fixedIntervalMs(300_000L)
                        .remark("Collector 扫描 chain_event，Admin 负责配置/冷却/发信")
                        .editable(false)
                        .remote(true)
                        .build(),
                PlatformJobDefinition.builder()
                        .jobKey(PlatformJobKeys.EXECUTOR_CHAIN_RELOAD)
                        .name("执行器链热加载")
                        .module("executor")
                        .scheduleKind(ScheduleKind.FIXED_DELAY)
                        .fixedIntervalMs(60_000L)
                        .remark("Executor 节点本地轮询 Admin 链变更并热加载（日志在节点侧）")
                        .editable(false)
                        .remote(true)
                        .build(),
                PlatformJobDefinition.builder()
                        .jobKey(PlatformJobKeys.EXECUTOR_HEARTBEAT)
                        .name("执行器注册心跳")
                        .module("executor")
                        .scheduleKind(ScheduleKind.FIXED_RATE)
                        .fixedIntervalMs(RegistryConstants.DEFAULT_HEARTBEAT_INTERVAL_SECONDS * 1000L)
                        .remark("Executor 向 Admin 注册中心发送心跳")
                        .editable(false)
                        .remote(true)
                        .build(),
                PlatformJobDefinition.builder()
                        .jobKey(PlatformJobKeys.COLLECTOR_HEARTBEAT)
                        .name("采集器注册心跳")
                        .module("collector")
                        .scheduleKind(ScheduleKind.FIXED_RATE)
                        .fixedIntervalMs(RegistryConstants.DEFAULT_HEARTBEAT_INTERVAL_SECONDS * 1000L)
                        .remark("Collector 向 Admin 注册中心发送心跳")
                        .editable(false)
                        .remote(true)
                        .build()
        );
    }
}
