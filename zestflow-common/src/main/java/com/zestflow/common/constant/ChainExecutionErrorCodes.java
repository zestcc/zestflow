package com.zestflow.common.constant;

/**
 * 链执行基础设施错误码 — 此类错误必须 fail-fast，不受 WRAPPED 失败策略影响。
 */
public final class ChainExecutionErrorCodes {

    private ChainExecutionErrorCodes() {}

    public static final String CHAIN_KEY_NOT_REGISTERED = "CHAIN_KEY_NOT_REGISTERED";
    public static final String CHAIN_NOT_FOUND = "CHAIN_NOT_FOUND";
    public static final String CHAIN_NOT_DESIGNED = "CHAIN_NOT_DESIGNED";
    public static final String CHAIN_NOT_PUBLISHED = "CHAIN_NOT_PUBLISHED";
    public static final String CHAIN_PUBLISHING = "CHAIN_PUBLISHING";
    public static final String CHAIN_DISABLED = "CHAIN_DISABLED";
    public static final String CHAIN_DEFINITION_NOT_LOADED = "CHAIN_DEFINITION_NOT_LOADED";

    public static boolean isInfrastructureError(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return false;
        }
        return errorCode.startsWith("CHAIN_");
    }
}
