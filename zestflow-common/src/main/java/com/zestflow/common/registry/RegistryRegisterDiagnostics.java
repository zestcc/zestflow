package com.zestflow.common.registry;

import com.zestflow.common.model.Result;

/**
 * 执行器/采集器向 Admin 注册失败时的可读诊断信息。
 */
public final class RegistryRegisterDiagnostics {

    private RegistryRegisterDiagnostics() {
    }

    /**
     * Admin 返回 JSON 但业务码非 200。
     */
    public static String describeResultFailure(Result<?> result) {
        if (result == null) {
            return "Admin 响应为空（HTTP 有 body 但解析后为空，或 Nginx 未反代 /api/registry/*，或 Admin 未启动）";
        }
        if (result.getCode() == 200) {
            return null;
        }
        String message = result.getMessage() != null && !result.getMessage().isBlank()
                ? result.getMessage()
                : "无 message";
        return switch (result.getCode()) {
            case 401 -> "Registry Token 无效或未配置：请对齐下游 registry-token 与 Admin 的 "
                    + "zestflow.admin.registry-token（生产环境必填，dev 可留空）: " + message;
            case 403 -> "被拒绝（常见：Nginx 防盗链/仅放行 GET 静态资源，未反代 POST /api/registry）: "
                    + message;
            case 404 -> "接口不存在：admin-addresses 应指向 Admin 根地址（能 POST "
                    + "/api/registry/register 或 /api/registry/collector/register），不是仅静态页: "
                    + message;
            case 502, 503, 504 -> "Admin 不可达或 Nginx 反代 upstream 失败（HTTP " + result.getCode()
                    + "，检查 Admin:8080 是否存活）: " + message;
            default -> "Admin 返回业务码 " + result.getCode() + ": " + message;
        };
    }

    /**
     * HTTP 客户端抛错（连接失败、超时、响应非 JSON 等）。
     */
    public static String describeException(Throwable e, boolean registryTokenConfigured) {
        String root = rootMessage(e);
        String lower = root.toLowerCase();

        if (lower.contains("connection refused")) {
            return "无法连接 Admin（Connection refused: " + root + "）。"
                    + "请检查：① Admin 是否已启动并监听 8080；"
                    + "② admin-addresses 是否写错（同机推荐 http://127.0.0.1:8080，"
                    + "经 Nginx 用 http://公网IP:端口 且 location / 须 proxy_pass 到 8080）；"
                    + "③ 若业务在本地、Admin 在云上，须能访问该 URL 且安全组放行";
        }
        if (lower.contains("connect timed out") || lower.contains("connection timed out")
                || lower.contains("read timed out")) {
            return "连接/读取超时（" + root + "）。请检查网络、防火墙、安全组，"
                    + "以及 Admin 是否过载；生产环境 registry-token 未配也可能表现为长时间重试后失败";
        }
        if (lower.contains("unknown host") || lower.contains("no route to host")) {
            return "DNS/路由不可达（" + root + "），请核对 admin-addresses 中的主机名或 IP";
        }
        if (lower.contains("401") || lower.contains("invalid registry token")) {
            return hintTokenMismatch(root, registryTokenConfigured);
        }
        if (lower.contains("403")) {
            return "HTTP 403（" + root + "）。浏览器能打开首页不代表 POST /api 可用；"
                    + "请确认 Nginx 整站反代到 Admin:8080，勿单独 alias /assets 或 valid_referers 拦截";
        }
        if (lower.contains("404")) {
            return "HTTP 404（" + root + "），admin-addresses 可能不是 Admin API 根地址";
        }
        if (lower.contains("json") || lower.contains("unrecognized token") || lower.contains("<html")) {
            return "响应非 JSON（" + root + "），可能访问到了 Nginx/HTML 错误页而非 Admin API；"
                    + "用 curl.exe -X POST .../api/registry/register 验证";
        }
        if (!registryTokenConfigured && (lower.contains("timeout") || lower.contains("timed out"))) {
            return "请求超时（" + root + "）。请检查网络；若 Admin 为 prod profile，"
                    + "下游必须配置 registry-token 且与 Admin 一致";
        }
        return root;
    }

    public static String hintForEmptyAddresses(String configKey) {
        return "未配置 " + configKey + "，无法向 Admin 注册。"
                + "示例：zestflow.executor.admin-addresses=http://127.0.0.1:8080"
                + "（采集器为 zestflow.collector.registry.admin-addresses）";
    }

    /**
     * 汇总所有 Admin 地址均失败时的 ERROR 日志正文。
     */
    public static String summarizeFailures(String roleLabel, String id, String adminAddresses,
                                           String adminAddressesKey, String registryTokenKey,
                                           String registerPath, String details) {
        String base = adminAddresses != null && !adminAddresses.isBlank() ? adminAddresses.trim() : "(未配置)";
        return roleLabel + " 向 Admin 注册失败"
                + " | id=" + id
                + " | " + adminAddressesKey + "=" + base
                + " | 失败详情: " + details
                + " | 排查清单:"
                + " (1) Admin 是否运行: curl -I " + base + "/"
                + " (2) 注册接口是否可达: curl.exe -X POST " + base + registerPath
                + " -H Content-Type:application/json [-H X-Registry-Token:令牌] -d \"{...}\""
                + " (3) " + registryTokenKey + " 与 Admin zestflow.admin.registry-token 是否一致"
                + " (4) 同机部署优先用 http://127.0.0.1:8080，公网 UI 端口(如10063)须整站反代/api"
                + " (5) 注册成功≠能执行链：Admin 还须能回连执行器 Netty(host:port)，本机内网 IP 公网 Admin 无法访问";
    }

    private static String hintTokenMismatch(String root, boolean registryTokenConfigured) {
        if (registryTokenConfigured) {
            return "Registry Token 校验失败（" + root + "）。"
                    + "请确认下游 registry-token 与 Admin application-prod.yml 中 "
                    + "zestflow.admin.registry-token 完全一致（区分大小写、无多余空格）";
        }
        return "Admin 要求 Registry Token，但下游未配置 registry-token（" + root + "）。"
                + "prod 环境 Admin 与 Executor/Collector 必须成对配置；local 可两边都留空";
    }

    private static String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg != null && !msg.isBlank() ? msg : cur.getClass().getSimpleName();
    }
}
