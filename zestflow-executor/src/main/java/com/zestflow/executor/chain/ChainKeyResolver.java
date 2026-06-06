package com.zestflow.executor.chain;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.constant.ChainExecutionErrorCodes;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.executor.registry.ExecutorProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * chain_key → chain_code 解析与执行前状态校验。
 */
@RequiredArgsConstructor
public class ChainKeyResolver {

    private final ChainRepository chainRepository;
    private final ExecutorProperties executorProperties;

    public ChainExecuteResultDTO resolveFailureByKey(String chainKey) {
        if (!StringUtils.hasText(chainKey)) {
            return infrastructureFailure(null, chainKey,
                    ChainExecutionErrorCodes.CHAIN_KEY_NOT_REGISTERED, "chain_key 不能为空");
        }
        ChainPO po = chainRepository.getByChainKey(executorProperties.getAppCode(), chainKey.trim());
        if (po == null) {
            return infrastructureFailure(null, chainKey,
                    ChainExecutionErrorCodes.CHAIN_KEY_NOT_REGISTERED,
                    "链未注册: chain_key=" + chainKey + "，请确认 @ZestChain 已扫描且应用已启动");
        }
        return readinessFailure(po);
    }

    public String resolveCode(String chainKey) {
        if (!StringUtils.hasText(chainKey)) {
            return null;
        }
        ChainPO po = chainRepository.getByChainKey(executorProperties.getAppCode(), chainKey.trim());
        return po != null ? po.getCode() : null;
    }

    /**
     * 解析 chain_key 并校验可执行性。
     */
    public ResolvedChainKey resolveKey(String chainKey) {
        if (!StringUtils.hasText(chainKey)) {
            return ResolvedChainKey.failure(infrastructureFailure(null, chainKey,
                    ChainExecutionErrorCodes.CHAIN_KEY_NOT_REGISTERED, "chain_key 不能为空"));
        }
        ChainPO po = chainRepository.getByChainKey(executorProperties.getAppCode(), chainKey.trim());
        if (po == null) {
            return ResolvedChainKey.failure(infrastructureFailure(null, chainKey,
                    ChainExecutionErrorCodes.CHAIN_KEY_NOT_REGISTERED,
                    "链未注册: chain_key=" + chainKey));
        }
        ChainExecuteResultDTO readiness = readinessFailure(po);
        if (readiness != null) {
            return ResolvedChainKey.failure(readiness);
        }
        return ResolvedChainKey.ok(po.getCode(), po.getChainKey());
    }

    public record ResolvedChainKey(String chainCode, String chainKey, ChainExecuteResultDTO failure) {
        public static ResolvedChainKey ok(String chainCode, String chainKey) {
            return new ResolvedChainKey(chainCode, chainKey, null);
        }

        public static ResolvedChainKey failure(ChainExecuteResultDTO failure) {
            return new ResolvedChainKey(null, null, failure);
        }

        public boolean isOk() {
            return failure == null && chainCode != null;
        }
    }

    public ChainExecuteResultDTO readinessFailure(String chainCode) {
        ChainPO po = chainRepository.get(chainCode);
        if (po == null) {
            // 无 DB 记录：运行时动态加载链（如 Demo 编排）不受发布状态约束
            return null;
        }
        return readinessFailure(po);
    }

    public ChainExecuteResultDTO readinessFailure(ChainPO po) {
        int st = po.getStatus() != null ? po.getStatus() : ChainLifecycleStatus.DESIGNING;
        return switch (st) {
            case ChainLifecycleStatus.DISABLED -> infrastructureFailure(po.getCode(), po.getChainKey(),
                    ChainExecutionErrorCodes.CHAIN_DISABLED,
                    "链已停用: " + label(po));
            case ChainLifecycleStatus.DESIGNING -> infrastructureFailure(po.getCode(), po.getChainKey(),
                    ChainExecutionErrorCodes.CHAIN_NOT_DESIGNED,
                    "链未设计，请在 Admin 完成编排: " + label(po));
            case ChainLifecycleStatus.UNPUBLISHED -> infrastructureFailure(po.getCode(), po.getChainKey(),
                    ChainExecutionErrorCodes.CHAIN_NOT_PUBLISHED,
                    "链未发布: " + label(po));
            case ChainLifecycleStatus.PUBLISHING -> infrastructureFailure(po.getCode(), po.getChainKey(),
                    ChainExecutionErrorCodes.CHAIN_PUBLISHING,
                    "链发布中，请稍后重试: " + label(po));
            case ChainLifecycleStatus.PUBLISHED -> null;
            default -> infrastructureFailure(po.getCode(), po.getChainKey(),
                    ChainExecutionErrorCodes.CHAIN_NOT_PUBLISHED,
                    "链不可执行(status=" + st + "): " + label(po));
        };
    }

    private static String label(ChainPO po) {
        if (po.getChainKey() != null && !po.getChainKey().isBlank()) {
            return po.getChainKey() + " (" + po.getCode() + ")";
        }
        return po.getCode();
    }

    public static ChainExecuteResultDTO infrastructureFailure(String chainCode, String chainKey,
                                                               String errorCode, String message) {
        return ChainExecuteResultDTO.builder()
                .chainCode(chainCode)
                .status(ChainConstants.CHAIN_FAILED)
                .errorCode(errorCode)
                .errorMessage(message)
                .costMs(0L)
                .build();
    }

    public static ChainExecuteResultDTO definitionNotLoaded(String chainCode) {
        return infrastructureFailure(chainCode, null,
                ChainExecutionErrorCodes.CHAIN_DEFINITION_NOT_LOADED,
                "链已发布但运行时定义未加载: " + chainCode);
    }
}
