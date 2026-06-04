package com.zestflow.admin.registry;

import java.util.OptionalLong;
import java.util.Set;

/**
 * 执行器/采集器存活状态存储 — 心跳只更新此层，不每 tick 写 DB（对标 xxl-job 内存注册表 / Nacos 实例缓存）。
 */
public interface RegistryLiveStore {

    void touchExecutor(String executorId);

    void touchCollector(String collectorId);

    void removeExecutor(String executorId);

    void removeCollector(String collectorId);

    /** Admin 启动或 register 时预热 */
    void seedExecutor(String executorId, long lastSeenEpochMs);

    void seedCollector(String collectorId, long lastSeenEpochMs);

    boolean isExecutorAlive(String executorId);

    boolean isCollectorAlive(String collectorId);

    boolean tracksExecutor(String executorId);

    boolean tracksCollector(String collectorId);

    OptionalLong executorLastSeenEpochMs(String executorId);

    OptionalLong collectorLastSeenEpochMs(String collectorId);

    Set<String> aliveExecutorIds();

    Set<String> aliveCollectorIds();
}
