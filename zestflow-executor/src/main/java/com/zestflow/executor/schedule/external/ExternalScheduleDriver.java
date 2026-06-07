package com.zestflow.executor.schedule.external;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import com.zestflow.common.spi.schedule.ScheduleDriver;
import com.zestflow.common.spi.schedule.ScheduleDriverIds;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 外部调度驱动 — 启动 xxl-job 执行器，不扫业务库 Cron。
 */
@Slf4j
@RequiredArgsConstructor
public class ExternalScheduleDriver implements ScheduleDriver {

    private final XxlJobSpringExecutor xxlJobExecutor;
    private final XxlJobScheduleProperties properties;

    @Override
    public String driverId() {
        return ScheduleDriverIds.EXTERNAL;
    }

    @Override
    public void start() {
        if (!properties.isEnabled()) {
            log.info("ExternalScheduleDriver: xxl-job 未启用，请使用 HTTP /execute 或启用 xxl-job");
            return;
        }
        try {
            xxlJobExecutor.start();
            log.info("ExternalScheduleDriver: xxl-job 执行器已启动 appname={} admin={}",
                    properties.getAppname(), properties.getAdminAddresses());
        } catch (Exception e) {
            log.error("ExternalScheduleDriver: xxl-job 启动失败", e);
            throw new IllegalStateException("xxl-job executor start failed", e);
        }
    }

    @Override
    public void stop() {
        try {
            xxlJobExecutor.destroy();
        } catch (Exception e) {
            log.warn("ExternalScheduleDriver: xxl-job 停止异常", e);
        }
    }
}
