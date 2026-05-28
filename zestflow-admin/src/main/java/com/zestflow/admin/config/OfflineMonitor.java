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
}
