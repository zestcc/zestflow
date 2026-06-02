package com.zestflow.admin.schedule.cluster;

import com.zestflow.admin.runtime.AdminDeployModeConditions;
import com.zestflow.admin.schedule.ScheduleScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 集群 Admin 调度轮询 — ShedLock + Redis，保证多副本仅一个节点执行 cron 扫描。
 */
@Slf4j
@Component
@Conditional(AdminDeployModeConditions.Cluster.class)
@RequiredArgsConstructor
public class ClusterScheduleMonitor {

    private static final String LOCK_NAME = "zestflow-admin-schedule-monitor-scan";

    private final ScheduleScanService scheduleScanService;

    @Scheduled(fixedDelay = 15_000)
    @SchedulerLock(
            name = LOCK_NAME,
            lockAtMostFor = "PT5M",
            lockAtLeastFor = "PT10S"
    )
    public void scan() {
        scheduleScanService.scanAndTriggerDueSchedules();
    }
}
