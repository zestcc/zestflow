package com.zestflow.admin.schedule.platform;

/**
 * 调度任务类型 — 对标 xxl-job「业务 Job / GLUE 系统任务」分层。
 */
public final class ScheduleJobType {

    public static final String CHAIN = "CHAIN";
    public static final String PLATFORM = "PLATFORM";

    private ScheduleJobType() {}
}
