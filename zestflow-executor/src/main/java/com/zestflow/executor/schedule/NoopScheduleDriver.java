package com.zestflow.executor.schedule;

import com.zestflow.common.spi.schedule.ScheduleDriver;
import com.zestflow.common.spi.schedule.ScheduleDriverIds;

/** 关闭本地 Cron 扫描（外部 xxl-job / HTTP 触发时使用） */
public class NoopScheduleDriver implements ScheduleDriver {

    @Override
    public String driverId() {
        return ScheduleDriverIds.NOOP;
    }

    @Override
    public void start() {
        // no-op
    }

    @Override
    public void stop() {
        // no-op
    }
}
