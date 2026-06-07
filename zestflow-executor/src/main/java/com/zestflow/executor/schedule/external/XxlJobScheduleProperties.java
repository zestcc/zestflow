package com.zestflow.executor.schedule.external;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "zestflow.executor.schedule.xxl-job")
public class XxlJobScheduleProperties {

    /** 是否启用 xxl-job 执行器（driver=external 时默认 true） */
    private boolean enabled = true;

    private String adminAddresses = "http://127.0.0.1:8080/xxl-job-admin";
    private String accessToken = "";
    private String appname = "zestflow-executor";
    private String address = "";
    private String ip = "";
    private int port = 9999;
    private String logPath = "/data/applogs/xxl-job/jobhandler";
    private int logRetentionDays = 30;

    /** @XxlJob 默认 handler 名 */
    private String defaultJobHandler = "zestflowChainJob";
}
