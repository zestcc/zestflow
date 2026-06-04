package com.zestflow.executor.route;

import com.zestflow.common.protocol.ChainHttpRouteConfig;
import com.zestflow.executor.chain.ChainDefinition;
import com.zestflow.executor.chain.ChainManager;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 链 HTTP 路由注册表 — 从 {@code chainData.config.http} 构建 path+method → chainCode 映射。
 * <p>
 * 对标 Spring Cloud Gateway / Servlet Filter 动态路由，优先级低于已有 {@code @RequestMapping}。
 */
@Slf4j
public class ChainRouteRegistry {

    private final ConcurrentHashMap<String, ChainRouteEntry> routes = new ConcurrentHashMap<>();

    public void refresh(ChainManager chainManager) {
        Map<String, ChainDefinition> active = chainManager.getActiveChains();
        ConcurrentHashMap<String, ChainRouteEntry> next = new ConcurrentHashMap<>();
        for (ChainDefinition def : active.values()) {
            ChainHttpRouteConfig cfg = ChainHttpRouteConfig.fromExtraConfig(def.getExtraConfig());
            if (cfg == null || cfg.getPath() == null || cfg.getPath().isBlank()) {
                continue;
            }
            String method = cfg.getMethod() != null && !cfg.getMethod().isBlank()
                    ? cfg.getMethod().trim().toUpperCase(Locale.ROOT) : "POST";
            String key = routeKey(method, normalizePath(cfg.getPath()));
            ChainRouteEntry existing = next.putIfAbsent(key, new ChainRouteEntry(def.getCode(), cfg));
            if (existing != null) {
                log.warn("链 HTTP 路由冲突 path={} method={} 已有={} 忽略={}",
                        cfg.getPath(), method, existing.getChainCode(), def.getCode());
            }
        }
        routes.clear();
        routes.putAll(next);
        log.info("链 HTTP 路由刷新完成 count={}", routes.size());
    }

    public Optional<ChainRouteEntry> lookup(String httpMethod, String requestPath) {
        if (httpMethod == null || requestPath == null) {
            return Optional.empty();
        }
        String key = routeKey(httpMethod.toUpperCase(Locale.ROOT), normalizePath(requestPath));
        return Optional.ofNullable(routes.get(key));
    }

    public int size() {
        return routes.size();
    }

    static String routeKey(String method, String path) {
        return method + " " + path;
    }

    static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        String p = path.trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }
}
