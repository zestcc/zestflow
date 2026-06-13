package com.zestflow.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 日志执行轨迹 SSE 实时流配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "zestflow.admin.log-live-stream")
public class LogLiveStreamProperties {

    /** Collector 轮询间隔（毫秒） */
    private long pollIntervalMs = 2_000L;

    /** SSE 连接最长存活时间（毫秒） */
    private long sseTimeoutMs = 600_000L;

    /** 后台推送线程池大小 */
    private int poolSize = Math.min(4, Math.max(2, Runtime.getRuntime().availableProcessors()));
}
