package com.zestflow.admin.playground.support;

import com.zestflow.admin.client.ExecutorProxyService;
import com.zestflow.common.util.PlaygroundUrlHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * 试验场 URL 解析
 * <p>
 * 默认（Admin 内网转发 Netty）：场景存相对路径 {@code /api/xxx}、{@code /execute}，20550 不对外暴露。
 * 仅当 Executor 配置了 {@code zestflow.playground.url}（Tomcat/网关）时，业务 API 使用完整 URL。
 */
@Component
@ConditionalOnProperty(prefix = "zestflow.playground", name = "enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class PlaygroundUrlResolver {

    private final ExecutorProxyService executorProxyService;

    /** Executor 侧 Tomcat/网关基址（可为空） */
    public String resolveTomcatBaseUrl(String appCode) {
        String url = executorProxyService.resolveExecutorPlaygroundUrl(appCode);
        return StringUtils.hasText(url) ? trimTrailingSlash(url.trim()) : "";
    }

    public boolean hasTomcatBase(String appCode) {
        return StringUtils.hasText(resolveTomcatBaseUrl(appCode));
    }

    /**
     * 入库路径：Tomcat 模式 → 完整 URL；否则 → 相对路径（兼容历史 Netty 绝对 URL 会归一化）
     */
    public String normalizeForStorage(String appCode, String path) {
        if (!StringUtils.hasText(path)) {
            return path;
        }
        String p = path.trim();
        p = stripInternalAbsoluteUrl(p);
        if (p.contains("://")) {
            return p;
        }
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        if (isExecuteRelativePath(p)) {
            return p;
        }
        String tomcat = resolveTomcatBaseUrl(appCode);
        if (StringUtils.hasText(tomcat) && p.startsWith("/api/")) {
            return PlaygroundUrlHelper.joinBaseUrl(tomcat, p);
        }
        return p;
    }

    /** 展示/导入：Tomcat 模式业务 API 拼完整 URL，其余保持相对路径 */
    public String toDisplayUrl(String appCode, String path) {
        return normalizeForStorage(appCode, path);
    }

    /** SSRF 白名单：仅 Tomcat 基址（若有） */
    public List<String> allowedBaseUrls(String appCode) {
        List<String> list = new ArrayList<>();
        String tomcat = resolveTomcatBaseUrl(appCode);
        if (StringUtils.hasText(tomcat)) {
            list.add(tomcat);
        }
        return list;
    }

    public boolean isTomcatBusinessUrl(String appCode, String fullUrl) {
        if (!StringUtils.hasText(fullUrl) || !fullUrl.contains("://")) {
            return false;
        }
        String tomcat = resolveTomcatBaseUrl(appCode);
        return StringUtils.hasText(tomcat) && fullUrl.startsWith(tomcat);
    }

    public boolean isExecutePath(String path) {
        if (!StringUtils.hasText(path)) {
            return false;
        }
        String p = stripInternalAbsoluteUrl(path.trim());
        if (p.equals("/execute") || p.startsWith("/execute?")) {
            return true;
        }
        if (!p.contains("://")) {
            return false;
        }
        try {
            return "/execute".equals(URI.create(p.split("#")[0]).getPath());
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isApiPath(String path) {
        if (!StringUtils.hasText(path)) {
            return false;
        }
        String p = stripInternalAbsoluteUrl(path.trim());
        if (p.startsWith("/api/")) {
            return true;
        }
        if (!p.contains("://")) {
            return false;
        }
        try {
            String rel = URI.create(p.split("#")[0]).getPath();
            return rel != null && rel.startsWith("/api/");
        } catch (Exception e) {
            return false;
        }
    }

    public String toRelativePath(String fullUrl) {
        return PlaygroundUrlHelper.toRelativePath(fullUrl);
    }

    /** 历史数据：Netty :20550 等内部绝对 URL → 相对路径 */
    public String stripInternalAbsoluteUrl(String path) {
        if (!StringUtils.hasText(path) || !path.contains("://")) {
            return path;
        }
        String p = path.trim();
        int apiIdx = p.indexOf("/api");
        if (apiIdx >= 0) {
            return p.substring(apiIdx);
        }
        int execIdx = p.indexOf("/execute");
        if (execIdx >= 0) {
            return p.substring(execIdx);
        }
        return p;
    }

    private static boolean isExecuteRelativePath(String path) {
        return path.equals("/execute") || path.startsWith("/execute?");
    }

    private static String trimTrailingSlash(String url) {
        String u = url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }
}
