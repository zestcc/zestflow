package com.zestflow.common.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 链 HTTP 路由与响应配置 — 来自 chainData.config.http
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChainHttpRouteConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 对外路径，如 /api/heytrip/ota/rc/getHotels */
    private String path;

    /** HTTP 方法，默认 POST */
    private String method;

    /** 响应 Content-Type，如 application/xml */
    private String produces;

    /** 请求 Content-Type */
    private String consumes;

    /** 失败时调用的错误处理元件 ID（@ZestErrorHandler） */
    private String errorHandler;

    /** Mode 1 失败策略覆盖（可选） */
    private ChainFailurePolicy failurePolicy;

    @SuppressWarnings("unchecked")
    public static ChainHttpRouteConfig fromExtraConfig(Map<String, Object> extraConfig) {
        if (extraConfig == null || extraConfig.isEmpty()) {
            return null;
        }
        Object http = extraConfig.get("http");
        if (!(http instanceof Map<?, ?> raw)) {
            return null;
        }
        ChainHttpRouteConfigBuilder builder = ChainHttpRouteConfig.builder();
        Object path = raw.get("path");
        if (path != null) {
            builder.path(String.valueOf(path).trim());
        }
        Object method = raw.get("method");
        if (method != null) {
            builder.method(String.valueOf(method).trim().toUpperCase());
        }
        Object produces = raw.get("produces");
        if (produces != null) {
            builder.produces(String.valueOf(produces).trim());
        }
        Object consumes = raw.get("consumes");
        if (consumes != null) {
            builder.consumes(String.valueOf(consumes).trim());
        }
        Object errorHandler = raw.get("errorHandler");
        if (errorHandler != null) {
            builder.errorHandler(String.valueOf(errorHandler).trim());
        }
        Object policy = raw.get("failurePolicy");
        if (policy != null) {
            try {
                builder.failurePolicy(ChainFailurePolicy.valueOf(String.valueOf(policy).trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // ignore invalid
            }
        }
        ChainHttpRouteConfig cfg = builder.build();
        if ((cfg.getPath() == null || cfg.getPath().isBlank())
                && cfg.getErrorHandler() == null
                && cfg.getProduces() == null) {
            return null;
        }
        return cfg;
    }
}
