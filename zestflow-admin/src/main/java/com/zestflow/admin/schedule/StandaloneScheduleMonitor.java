package com.zestflow.admin.schedule;

import com.zestflow.admin.runtime.AdminDeployModeConditions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 单机部署调度轮询 — {@code deploy-mode=standalone} 时启用，无 ShedLock。
 */
@Slf4j
@Component
@Conditional(AdminDeployModeConditions.Standalone.class)
@RequiredArgsConstructor
public class StandaloneScheduleMonitor {

    private final ScheduleScanService scheduleScanService;

    @Scheduled(fixedDelay = 15_000)
    public void scan() {
        scheduleScanService.scanAndTriggerDueSchedules();
    }
}
