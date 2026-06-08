package com.zestflow.common.util;

import com.zestflow.common.constant.ChainExecutionErrorCodes;

import java.util.Locale;

/**
 * 链执行业务失败 HTTP 状态映射 — 避免 BizException 语义被统一包装为 500。
 */
public final class ChainExecutionHttpStatus {

    private ChainExecutionHttpStatus() {
    }

    /**
     * @return HTTP 状态码（400/401/403/404/500）
     */
    public static int resolve(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return 500;
        }
        if (ChainExecutionErrorCodes.isInfrastructureError(errorCode)) {
            return 500;
        }
        String upper = errorCode.toUpperCase(Locale.ROOT);
        if (upper.contains("UNAUTHORIZED") || upper.equals("401")) {
            return 401;
        }
        if (upper.contains("FORBIDDEN") || upper.contains("ACCESS_DENIED")
                || upper.contains("PERMISSION_DENIED")) {
            return 403;
        }
        if (upper.contains("NOT_FOUND") || upper.endsWith("_NF")) {
            return 404;
        }
        if (upper.contains("VALIDATION") || upper.contains("INVALID") || upper.contains("BAD_REQUEST")) {
            return 400;
        }
        return 400;
    }
}
