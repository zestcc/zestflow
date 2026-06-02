package com.zestflow.executor.chain;

import com.zestflow.executor.design.DesignPO;
import com.zestflow.executor.design.DesignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 链定义轮询热加载 — 对标 LiteFlow/Nacos 配置轮询刷新。
 * <p>
 * 以 chain/design 更新时间 + status 指纹比对，变更时调用 {@link ChainLoader#reloadFromDatabase}，
 * 不递增 DB 版本，避免与 Admin 发布路径形成 reload 死循环。
 */
@Slf4j
@RequiredArgsConstructor
public class ChainReloadMonitor {

    private final ChainRepository chainRepo;
    private final DesignRepository designRepo;
    private final ChainManager chainManager;
    private final ChainLoader chainLoader;

    /** 上次同步指纹 chainCode → updatedAt|designUpdatedAt|status|version */
    private final Map<String, String> lastSyncFingerprint = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${zestflow.executor.chain.reload-check-interval-ms:60000}")
    public void pollAndReload() {
        try {
            List<ChainPO> chains = chainRepo.list(null, null);
            Set<String> loadedCodes = chainManager.getActiveCodes();

            for (ChainPO chain : chains) {
                if (chain.getStatus() == null || chain.getStatus() < 3) {
                    if (loadedCodes.contains(chain.getCode())) {
                        chainManager.unload(chain.getCode());
                        lastSyncFingerprint.remove(chain.getCode());
                        log.info("链已停用/未发布，卸载 code={}", chain.getCode());
                    }
                    continue;
                }
                if (chain.getDesignCode() == null || chain.getDesignCode().isEmpty()) {
                    continue;
                }
                DesignPO design = designRepo.get(chain.getDesignCode());
                String fingerprint = buildFingerprint(chain, design != null ? design.getUpdatedAt() : null);
                if (fingerprint.equals(lastSyncFingerprint.get(chain.getCode()))) {
                    continue;
                }
                ChainLoader.ChainReloadResult result = chainLoader.reloadFromDatabase(chain.getCode());
                if (result.isSuccess()) {
                    lastSyncFingerprint.put(chain.getCode(), fingerprint);
                    log.info("轮询热加载成功 code={} nodes={}", chain.getCode(), result.getNodeCount());
                } else {
                    log.warn("轮询热加载失败 code={} reason={}", chain.getCode(), result.getErrorMessage());
                }
            }
        } catch (Exception e) {
            log.error("链轮询热加载异常", e);
        }
    }

    private static String buildFingerprint(ChainPO chain, String designUpdatedAt) {
        return String.valueOf(chain.getUpdatedAt()) + "|"
                + String.valueOf(designUpdatedAt) + "|"
                + chain.getStatus() + "|"
                + chain.getVersion();
    }
}
