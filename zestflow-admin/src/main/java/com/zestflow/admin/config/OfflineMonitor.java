package com.zestflow.admin.config;

import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.common.constant.RegistryConstants;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class OfflineMonitor {

    private final ExecutorRegistryMapper executorRegistryMapper;

    private static final int DEAD_TIMEOUT_SECONDS =
            RegistryConstants.DEFAULT_HEARTBEAT_INTERVAL_SECONDS * RegistryConstants.DEAD_TIMEOUT_MULTIPLIER;

    /** ABNORMAL 记录超过此时间未恢复则物理删除（24 小时） */
    private static final int ABNORMAL_CLEANUP_HOURS = 24;

    @Scheduled(fixedRate = 30_000)
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
    }

    /**
     * 清理长期异常离线的执行器记录
     * <p>
     * 每 30 分钟执行一次，删除 ABNORMAL 状态超过 24h 的记录。
     * 避免注册表随实例轮换无限膨胀。
     */
    @Scheduled(fixedRate = 1_800_000)
    public void cleanupStaleAbnormal() {
        LocalDateTime deadline = LocalDateTime.now().minusHours(ABNORMAL_CLEANUP_HOURS);

        int deleted = executorRegistryMapper.delete(
                Wrappers.<ExecutorRegistryPO>lambdaQuery()
                        .eq(ExecutorRegistryPO::getStatus, RegistryConstants.STATUS_ABNORMAL)
                        .lt(ExecutorRegistryPO::getLastHeartbeat, deadline));

        if (deleted > 0) {
            log.info("清理过期异常记录：{} 条（超过 {}h 未恢复）", deleted, ABNORMAL_CLEANUP_HOURS);
        }
    }
}
