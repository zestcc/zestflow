package com.zestflow.collector.jdbc.config;

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
}
