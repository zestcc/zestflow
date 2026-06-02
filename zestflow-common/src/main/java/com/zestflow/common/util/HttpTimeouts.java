package com.zestflow.common.util;

/**
 * HTTP 客户端超时默认值（毫秒）
 */
public final class HttpTimeouts {

    public static final int DEFAULT_CONNECT_MS = 5_000;
    public static final int DEFAULT_READ_MS = 5_000;

    private HttpTimeouts() {
    }
}
