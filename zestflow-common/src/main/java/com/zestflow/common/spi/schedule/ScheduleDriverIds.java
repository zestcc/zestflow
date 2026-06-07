package com.zestflow.common.spi.schedule;

public final class ScheduleDriverIds {

    public static final String EMBEDDED = "embedded";
    public static final String NOOP = "noop";
    /** 预留：由 xxl-job 等外部系统触发，不启用本地 Cron 扫描 */
    public static final String EXTERNAL = "external";

    private ScheduleDriverIds() {}
}
