package com.zestflow.executor.schedule;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "zestflow.executor.schedule")
public class ExecutorScheduleProperties {

    /** 是否启用嵌入式链 Cron */
    private boolean enabled = true;

    /** 驱动类型：embedded | noop | external（预留） */
    private String driver = "embedded";

    /** 扫描间隔（毫秒） */
    private long pollIntervalMs = 15_000L;
}
