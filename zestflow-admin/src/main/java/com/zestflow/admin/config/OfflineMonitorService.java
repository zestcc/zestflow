package com.zestflow.admin.config;

import com.zestflow.admin.model.entity.CollectorRegistryPO;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.repository.CollectorRegistryMapper;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.common.constant.RegistryConstants;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 执行器/采集器离线检测与过期异常记录清理 — 供单机 / 集群调度入口复用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfflineMonitorService {

    private final ExecutorRegistryMapper executorRegistryMapper;
    private final CollectorRegistryMapper collectorRegistryMapper;

    private static final int DEAD_TIMEOUT_SECONDS =
            RegistryConstants.DEFAULT_HEARTBEAT_INTERVAL_SECONDS * RegistryConstants.DEAD_TIMEOUT_MULTIPLIER;

    /** ABNORMAL 记录超过此时间未恢复则物理删除（24 小时） */
    private static final int ABNORMAL_CLEANUP_HOURS = 24;

    public void checkOffline() {
        LocalDateTime deadline = LocalDateTime.now().minusSeconds(DEAD_TIMEOUT_SECONDS);

        int updated = executorRegistryMapper.update(null,
                Wrappers.<ExecutorRegistryPO>lambdaUpdate()
                        .set(ExecutorRegistryPO::getStatus, RegistryConstants.STATUS_ABNORMAL)
                        .eq(ExecutorRegistryPO::getStatus, RegistryConstants.STATUS_ONLINE)
                        .lt(ExecutorRegistryPO::getLastHeartbeat, deadline));

        if (updated > 0) {
            log.warn("离线检测：{} 个执行器已标记为异常离线（超时 {}s）", updated, DEAD_TIMEOUT_SECONDS);
        }

        int collectorUpdated = collectorRegistryMapper.update(null,
                Wrappers.<CollectorRegistryPO>lambdaUpdate()
                        .set(CollectorRegistryPO::getStatus, RegistryConstants.STATUS_ABNORMAL)
                        .eq(CollectorRegistryPO::getStatus, RegistryConstants.STATUS_ONLINE)
                        .lt(CollectorRegistryPO::getLastHeartbeat, deadline));

        if (collectorUpdated > 0) {
            log.warn("离线检测：{} 个采集器已标记为异常离线（超时 {}s）", collectorUpdated, DEAD_TIMEOUT_SECONDS);
        }
    }

    public void cleanupStaleAbnormal() {
        LocalDateTime deadline = LocalDateTime.now().minusHours(ABNORMAL_CLEANUP_HOURS);

        int deleted = executorRegistryMapper.delete(
                Wrappers.<ExecutorRegistryPO>lambdaQuery()
                        .eq(ExecutorRegistryPO::getStatus, RegistryConstants.STATUS_ABNORMAL)
                        .lt(ExecutorRegistryPO::getLastHeartbeat, deadline));

        if (deleted > 0) {
            log.info("清理过期异常记录：{} 条（超过 {}h 未恢复）", deleted, ABNORMAL_CLEANUP_HOURS);
        }

        int collectorDeleted = collectorRegistryMapper.delete(
                Wrappers.<CollectorRegistryPO>lambdaQuery()
                        .eq(CollectorRegistryPO::getStatus, RegistryConstants.STATUS_ABNORMAL)
                        .lt(CollectorRegistryPO::getLastHeartbeat, deadline));

        if (collectorDeleted > 0) {
            log.info("清理过期异常采集器记录：{} 条（超过 {}h 未恢复）", collectorDeleted, ABNORMAL_CLEANUP_HOURS);
        }
    }
}
