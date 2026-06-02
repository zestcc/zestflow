package com.zestflow.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Admin 业务缓存配置 — 与 {@code zestflow.admin.deploy-mode} 独立。
 */
@Data
@Component
@ConfigurationProperties(prefix = "zestflow.admin.cache")
public class AdminCacheProperties {

    /** 缓存类型：simple / caffeine（默认）/ redis */
    private String type = "caffeine";

    /** Caffeine 最大条目数 */
    private long maximumSize = 10_000;

    /** 缓存 TTL（秒） */
    private long ttlSeconds = 60;

    /** 是否启用 Caffeine 统计 */
    private boolean recordStats = false;
}
