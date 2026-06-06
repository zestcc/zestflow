package com.zestflow.common.constant;

/**
 * Admin 控制台 REST API 统一前缀（公网经 Nginx 仅放行此前缀下的路径）。
 */
public final class AdminApiPaths {

    public static final String PREFIX = "/api/zestflow";

    private AdminApiPaths() {
    }

    /** 拼接 Admin API 路径，如 {@code of("/auth/login")} → {@code /api/zestflow/auth/login} */
    public static String of(String subPath) {
        if (subPath == null || subPath.isEmpty()) {
            return PREFIX;
        }
        return subPath.startsWith("/") ? PREFIX + subPath : PREFIX + "/" + subPath;
    }
}
