package com.zestflow.common.spi.schedule;

/**
 * 调度触发源 SPI — 默认 {@link #driverId()} {@code embedded} 由 Executor 读业务库本地 Cron；
 * 可扩展 xxl-job、HTTP 回调等外部调度。
 */
public interface ScheduleDriver {

    /** 驱动标识，如 embedded / xxl-job / noop */
    String driverId();

    /** 启动驱动（注册本地 Cron、订阅外部触发等） */
    void start();

    /** 停止驱动 */
    void stop();
}
