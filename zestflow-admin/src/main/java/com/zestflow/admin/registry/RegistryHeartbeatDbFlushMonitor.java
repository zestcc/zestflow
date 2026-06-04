package com.zestflow.admin.registry;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zestflow.admin.model.entity.CollectorRegistryPO;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.repository.CollectorRegistryMapper;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.common.constant.RegistryConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.OptionalLong;

/**
 * 定时将内存心跳时间刷入 DB — 仅供控制台展示，不参与存活判定。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegistryHeartbeatDbFlushMonitor {

    private final RegistryLiveStore liveStore;
    private final ExecutorRegistryMapper executorRegistryMapper;
    private final CollectorRegistryMapper collectorRegistryMapper;

    @Scheduled(fixedRate = RegistryConstants.HEARTBEAT_DB_FLUSH_INTERVAL_MS)
    public void flushLastHeartbeatToDb() {
        int executors = flushExecutors();
        int collectors = flushCollectors();
        if (executors > 0 || collectors > 0) {
            log.debug("心跳刷库 executors={} collectors={}", executors, collectors);
        }
    }

    private int flushExecutors() {
        int updated = 0;
        for (String executorId : liveStore.aliveExecutorIds()) {
            OptionalLong epoch = liveStore.executorLastSeenEpochMs(executorId);
            if (epoch.isEmpty()) {
                continue;
            }
            LocalDateTime heartbeat = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(epoch.getAsLong()), ZoneId.systemDefault());
            int rows = executorRegistryMapper.update(null,
                    new LambdaUpdateWrapper<ExecutorRegistryPO>()
                            .set(ExecutorRegistryPO::getLastHeartbeat, heartbeat)
                            .eq(ExecutorRegistryPO::getExecutorId, executorId)
                            .in(ExecutorRegistryPO::getStatus,
                                    RegistryConstants.STATUS_ONLINE,
                                    RegistryConstants.STATUS_ABNORMAL));
            updated += rows;
        }
        return updated;
    }

    private int flushCollectors() {
        int updated = 0;
        for (String collectorId : liveStore.aliveCollectorIds()) {
            OptionalLong epoch = liveStore.collectorLastSeenEpochMs(collectorId);
            if (epoch.isEmpty()) {
                continue;
            }
            LocalDateTime heartbeat = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(epoch.getAsLong()), ZoneId.systemDefault());
            int rows = collectorRegistryMapper.update(null,
                    new LambdaUpdateWrapper<CollectorRegistryPO>()
                            .set(CollectorRegistryPO::getLastHeartbeat, heartbeat)
                            .eq(CollectorRegistryPO::getCollectorId, collectorId)
                            .in(CollectorRegistryPO::getStatus,
                                    RegistryConstants.STATUS_ONLINE,
                                    RegistryConstants.STATUS_ABNORMAL));
            updated += rows;
        }
        return updated;
    }
}
