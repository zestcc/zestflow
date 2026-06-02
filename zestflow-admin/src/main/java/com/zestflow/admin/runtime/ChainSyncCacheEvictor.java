package com.zestflow.admin.runtime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 链同步状态缓存清理 — 供单机 / 集群调度入口复用。
 */
@Service
@RequiredArgsConstructor
public class ChainSyncCacheEvictor {

    private static final Duration SYNC_CACHE_TTL = Duration.ofMinutes(5);

    private final AdminRuntimeStateStore runtimeStateStore;

    public void evictStale() {
        long cutoff = System.currentTimeMillis() - SYNC_CACHE_TTL.toMillis();
        runtimeStateStore.evictStaleChainSync(cutoff);
    }
}
