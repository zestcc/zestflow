package com.zestflow.collector.jdbc.config;

import com.zestflow.collector.async.AsyncCollectorSettings;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Collector JDBC 配置属性
 */
@Data
@ConfigurationProperties(prefix = "zestflow.collector")
public class CollectorProperties {

    /** 认证令牌（Admin 查询时需携带） */
    private String accessToken;

    /** 批量写入每批大小 */
    private int batchSize = 200;

    /** 批量写入最大间隔（毫秒） */
    private int batchMaxWaitMs = 500;

    /** 采集线程池大小 */
    private int poolSize = 1;

    /** 采集队列容量 */
    private int queueCapacity = 8192;

    /** 是否启用磁盘降级（队列满时写入本地文件） */
    private boolean diskFallbackEnabled = false;

    /** 磁盘降级目录 */
    private String diskFallbackDir = "./collector-fallback";

    /** 是否启用异步采集（队列 + 批量写入） */
    private boolean asyncEnabled = true;

    /** 熔断阈值（连续失败次数，0 表示不熔断） */
    private int circuitBreakerThreshold = 10;

    /** 熔断冷却时间（毫秒） */
    private int circuitBreakerCooldownMs = 30_000;

    /** 关闭等待时间（毫秒） */
    private long shutdownTimeoutMs = 5000;

    /** HTTP 客户端超时（毫秒，注册/心跳） */
    private int httpTimeoutMs = 5000;

    /** 磁盘降级回放间隔（毫秒） */
    private long diskReplayIntervalMs = 5000;

    public AsyncCollectorSettings toAsyncSettings() {
        return new AsyncCollectorSettings(
                batchSize, batchMaxWaitMs, queueCapacity,
                diskFallbackEnabled, diskFallbackDir,
                circuitBreakerThreshold, circuitBreakerCooldownMs,
                shutdownTimeoutMs, diskReplayIntervalMs, poolSize);
    }
}
