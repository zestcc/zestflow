package com.zestflow.admin.registry;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zestflow.admin.model.entity.CollectorRegistryPO;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.repository.CollectorRegistryMapper;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.common.constant.RegistryConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 注册生命周期 — 心跳事件同步 DB + 过期离线（替代定时扫库与 heartbeat-flush）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistryLifecycleService {

    private final RegistryLiveStore liveStore;
    private final RegistryExpiryScheduler expiryScheduler;
    private final ExecutorRegistryMapper executorRegistryMapper;
    private final CollectorRegistryMapper collectorRegistryMapper;

    public void onExecutorHeartbeat(String executorId) {
        syncExecutorOnline(executorId);
        expiryScheduler.scheduleExecutorExpiryCheck(
                executorId,
                RegistryConstants.deadTimeoutMillis(),
                () -> markExecutorOfflineIfExpired(executorId));
    }

    public void onCollectorHeartbeat(String collectorId) {
        syncCollectorOnline(collectorId);
        expiryScheduler.scheduleCollectorExpiryCheck(
                collectorId,
                RegistryConstants.deadTimeoutMillis(),
                () -> markCollectorOfflineIfExpired(collectorId));
    }

    public void onExecutorRemoved(String executorId) {
        expiryScheduler.cancelExecutor(executorId);
    }

    public void onCollectorRemoved(String collectorId) {
        expiryScheduler.cancelCollector(collectorId);
    }

    void markExecutorOfflineIfExpired(String executorId) {
        if (liveStore.isExecutorAlive(executorId)) {
            onExecutorHeartbeat(executorId);
            return;
        }
        int rows = executorRegistryMapper.update(null,
                new LambdaUpdateWrapper<ExecutorRegistryPO>()
                        .set(ExecutorRegistryPO::getStatus, RegistryConstants.STATUS_ABNORMAL)
                        .set(ExecutorRegistryPO::getLastHeartbeat, LocalDateTime.now())
                        .eq(ExecutorRegistryPO::getExecutorId, executorId)
                        .eq(ExecutorRegistryPO::getStatus, RegistryConstants.STATUS_ONLINE));
        if (rows > 0) {
            log.warn("执行器心跳超时已标记异常 executorId={}", executorId);
        }
    }

    void markCollectorOfflineIfExpired(String collectorId) {
        if (liveStore.isCollectorAlive(collectorId)) {
            onCollectorHeartbeat(collectorId);
            return;
        }
        int rows = collectorRegistryMapper.update(null,
                new LambdaUpdateWrapper<CollectorRegistryPO>()
                        .set(CollectorRegistryPO::getStatus, RegistryConstants.STATUS_ABNORMAL)
                        .set(CollectorRegistryPO::getLastHeartbeat, LocalDateTime.now())
                        .eq(CollectorRegistryPO::getCollectorId, collectorId)
                        .eq(CollectorRegistryPO::getStatus, RegistryConstants.STATUS_ONLINE));
        if (rows > 0) {
            log.warn("采集器心跳超时已标记异常 collectorId={}", collectorId);
        }
    }

    private void syncExecutorOnline(String executorId) {
        executorRegistryMapper.update(null,
                new LambdaUpdateWrapper<ExecutorRegistryPO>()
                        .set(ExecutorRegistryPO::getLastHeartbeat, LocalDateTime.now())
                        .set(ExecutorRegistryPO::getStatus, RegistryConstants.STATUS_ONLINE)
                        .eq(ExecutorRegistryPO::getExecutorId, executorId)
                        .in(ExecutorRegistryPO::getStatus,
                                RegistryConstants.STATUS_ONLINE,
                                RegistryConstants.STATUS_ABNORMAL));
    }

    private void syncCollectorOnline(String collectorId) {
        collectorRegistryMapper.update(null,
                new LambdaUpdateWrapper<CollectorRegistryPO>()
                        .set(CollectorRegistryPO::getLastHeartbeat, LocalDateTime.now())
                        .set(CollectorRegistryPO::getStatus, RegistryConstants.STATUS_ONLINE)
                        .eq(CollectorRegistryPO::getCollectorId, collectorId)
                        .in(CollectorRegistryPO::getStatus,
                                RegistryConstants.STATUS_ONLINE,
                                RegistryConstants.STATUS_ABNORMAL));
    }
}
