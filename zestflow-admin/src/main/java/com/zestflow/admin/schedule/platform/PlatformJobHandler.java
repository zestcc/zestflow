package com.zestflow.admin.schedule.platform;

@FunctionalInterface
public interface PlatformJobHandler {

    /** @return 可选执行摘要，写入 schedule_log.result_data */
    String execute() throws Exception;
}
