package com.zestflow.admin.runtime;

import com.zestflow.common.model.dto.ChainSyncDTO;

import java.util.Map;
import java.util.Optional;

/**
 * Admin 运行时状态存储 — 发布进度、链同步状态等（仅 Admin 模块）。
 * <p>
 * 由 {@code zestflow.admin.deploy-mode} 决定后端：
 * standalone → 本地内存；cluster → Redis。
 * 与 {@code zestflow.admin.cache.type} 无关。
 */
public interface AdminRuntimeStateStore {

    void savePublishProgress(String chainCode, int success, int total);

    Optional<int[]> getPublishProgress(String chainCode);

    void saveChainSync(ChainSyncDTO sync);

    Optional<ChainSyncDTO> getChainSync(String executorId);

    Map<String, ChainSyncDTO> getAllChainSync();

    void evictStaleChainSync(long cutoffTimestampMs);
}
