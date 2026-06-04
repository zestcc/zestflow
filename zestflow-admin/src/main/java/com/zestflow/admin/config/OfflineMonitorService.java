package com.zestflow.admin.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zestflow.admin.model.entity.CollectorRegistryPO;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.registry.RegistryLiveStore;
import com.zestflow.admin.repository.CollectorRegistryMapper;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.common.constant.RegistryConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 执行器/采集器离线检测与过期异常记录清理 — 基于内存/Redis 存活表，不依赖 DB last_heartbeat。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfflineMonitorService {

    private final ExecutorRegistryMapper executorRegistryMapper;
    private final CollectorRegistryMapper collectorRegistryMapper;
    private final RegistryLiveStore liveStore;

    /** ABNORMAL 记录超过此时间未恢复则物理删除（24 小时） */
    private static final int ABNORMAL_CLEANUP_HOURS = 24;

    public void checkOffline() {
        int executorOffline = reconcileExecutors();
        int collectorOffline = reconcileCollectors();
        if (executorOffline > 0) {
            log.warn("离线检测：{} 个执行器已标记为异常离线", executorOffline);
        }
        if (collectorOffline > 0) {
            log.warn("离线检测：{} 个采集器已标记为异常离线", collectorOffline);
        }
    }

    private int reconcileExecutors() {
        List<ExecutorRegistryPO> candidates = executorRegistryMapper.selectList(
                new LambdaQueryWrapper<ExecutorRegistryPO>()
                        .in(ExecutorRegistryPO::getStatus,
                                RegistryConstants.STATUS_ONLINE,
                                RegistryConstants.STATUS_ABNORMAL));
        int changed = 0;
        for (ExecutorRegistryPO po : candidates) {
            boolean alive = liveStore.isExecutorAlive(po.getExecutorId());
            if (po.getStatus() == RegistryConstants.STATUS_ONLINE && !alive) {
                po.setStatus(RegistryConstants.STATUS_ABNORMAL);
                po.setLastHeartbeat(LocalDateTime.now());
                executorRegistryMapper.updateById(po);
                changed++;
            } else if (po.getStatus() == RegistryConstants.STATUS_ABNORMAL && alive) {
                po.setStatus(RegistryConstants.STATUS_ONLINE);
                po.setLastHeartbeat(LocalDateTime.now());
                executorRegistryMapper.updateById(po);
                log.info("执行器恢复在线 executorId={}", po.getExecutorId());
            }
        }
        return changed;
    }

    private int reconcileCollectors() {
        List<CollectorRegistryPO> candidates = collectorRegistryMapper.selectList(
                new LambdaQueryWrapper<CollectorRegistryPO>()
                        .in(CollectorRegistryPO::getStatus,
                                RegistryConstants.STATUS_ONLINE,
                                RegistryConstants.STATUS_ABNORMAL));
        int changed = 0;
        for (CollectorRegistryPO po : candidates) {
            boolean alive = liveStore.isCollectorAlive(po.getCollectorId());
            if (po.getStatus() == RegistryConstants.STATUS_ONLINE && !alive) {
                po.setStatus(RegistryConstants.STATUS_ABNORMAL);
                po.setLastHeartbeat(LocalDateTime.now());
                collectorRegistryMapper.updateById(po);
                changed++;
            } else if (po.getStatus() == RegistryConstants.STATUS_ABNORMAL && alive) {
                po.setStatus(RegistryConstants.STATUS_ONLINE);
                po.setLastHeartbeat(LocalDateTime.now());
                collectorRegistryMapper.updateById(po);
                log.info("采集器恢复在线 collectorId={}", po.getCollectorId());
            }
        }
        return changed;
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
