package com.zestflow.admin.schedule.platform;

/**
 * 调度触发方式。
 */
public final class ScheduleKind {

    public static final String CRON = "CRON";
    public static final String FIXED_RATE = "FIXED_RATE";
    public static final String FIXED_DELAY = "FIXED_DELAY";

    private ScheduleKind() {}
}
