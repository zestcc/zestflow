package com.zestflow.admin.client.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Executor 读代理快照缓存 — Executor 离线时 Admin 仍可浏览链/设计/元件列表。
 */
@Data
@ConfigurationProperties(prefix = "zestflow.admin.executor-read-cache")
public class ExecutorReadCacheProperties {

    private boolean enabled = true;

    /** 快照 TTL（分钟） */
    private int ttlMinutes = 60;

    /** 最大缓存条目数 */
    private int maxEntries = 500;
}
