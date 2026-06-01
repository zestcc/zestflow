package com.zestflow.admin.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Executor HTTP 代理服务
 * <p>
 * Admin 不存储任何业务数据，通过此服务将所有业务读写操作代理到
 * 具体 Executor 的 Netty HTTP 端点。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutorProxyService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestTemplate restTemplate;
    private final ExecutorRegistryMapper executorRegistryMapper;

    /** 服务间通信协议（http/https） */
    @Value("${zestflow.admin.protocol:http}")
    private String protocol;

    /**
     * 通过 appCode 解析到 Executor 地址并执行 GET 请求
     *
     * @param appCode 应用编码
     * @param path    Executor API 路径，如 /api/chains
     * @param query   查询参数字符串（含 ?），如 "?keyword=xx&status=1&page=1&size=10"
     * @return 等价的空数据 JSON（executor 不可达时）
     */
    public String getFromExecutor(String appCode, String path, String query) {
        String baseUrl = resolveExecutorBaseUrl(appCode);
        if (baseUrl == null) {
            return emptyPage();
        }
        String url = baseUrl + path + (query != null ? query : "");
        try {
            String json = restTemplate.getForObject(url, String.class);
            if (json == null) return emptyPage();
            return enrichWithAppCode(json, appCode, baseUrl.replace(protocol + "://", ""));
        } catch (ResourceAccessException e) {
            log.warn("Executor 不可达 appCode={} url={}", appCode, url);
            return emptyPage();
        } catch (Exception e) {
            log.error("代理 GET 请求失败 appCode={} url={}", appCode, url, e);
            return emptyPage();
        }
    }

    private String buildUrl(String base, String query) {
        if (query == null || query.isEmpty()) return base;
        return base + query;
    }

    /**
     * 通过 appCode 解析到 Executor 地址并执行 GET 请求（不分页，返回数组）
     */
    public String getArrayFromExecutor(String appCode, String path, String query) {
        String url = resolveExecutorUrl(appCode, path, query);
        if (url == null) {
            return "[]";
        }
        try {
            String json = restTemplate.getForObject(url, String.class);
            if (json == null) return "[]";
            // 尝试解析为分页格式，提取 records
            JsonNode root = MAPPER.readTree(json);
            if (root.has("records")) {
                enrichRecords(root.get("records"), appCode);
                return MAPPER.writeValueAsString(root.get("records"));
            }
            return json;
        } catch (ResourceAccessException e) {
            log.warn("Executor 不可达 appCode={}", appCode);
            return "[]";
        } catch (Exception e) {
            log.error("代理 GET 请求失败 appCode={}", appCode, e);
            return "[]";
        }
    }

    /**
     * 通过 Executor 地址直接 GET（不经过 appCode 解析）
     */
    public String getDirect(String host, int port, String path, String query) {
        try {
            String url = protocol + "://" + host + ":" + port + path + (query != null ? query : "");
            String json = restTemplate.getForObject(url, String.class);
            return json;
        } catch (ResourceAccessException e) {
            log.warn("Executor 不可达 {}:{}", host, port);
            return emptyPage();
        } catch (Exception e) {
            log.error("代理 GET 请求失败 {}:{}", host, port, e);
            return emptyPage();
        }
    }

    /**
     * 通过拼接完整的 URL 直接 GET
     */
    public String getDirectFromUrl(String url, String query) {
        try {
            String fullUrl = url + (query != null ? query : "");
            String json = restTemplate.getForObject(fullUrl, String.class);
            return json;
        } catch (ResourceAccessException e) {
            log.warn("Executor 不可达 url={}", url);
            return emptyPage();
        } catch (Exception e) {
            log.error("代理 GET 请求失败 url={}", url, e);
            return emptyPage();
        }
    }

    /**
     * 通过 appCode 解析到 Executor 并执行 POST/PUT/DELETE
     */
    public String executeOnExecutor(String appCode, String method, String path, String body) {
        String url = resolveExecutorUrl(appCode, path, null);
        if (url == null) {
            return "{\"code\":500,\"message\":\"无可用执行器\"}";
        }
        try {
            String json;
            switch (method.toUpperCase()) {
                case "POST":
                    json = restTemplate.postForObject(url, body, String.class);
                    break;
                case "PUT":
                    json = restTemplate.exchange(
                            org.springframework.http.RequestEntity
                                    .put(new java.net.URI(url))
                                    .body(body),
                            String.class).getBody();
                    break;
                case "DELETE":
                    json = restTemplate.exchange(
                            org.springframework.http.RequestEntity
                                    .delete(new java.net.URI(url))
                                    .build(),
                            String.class).getBody();
                    break;
                default:
                    return "{\"code\":405,\"message\":\"不支持的请求方法\"}";
            }
            return enrichWithAppCode(json, appCode);
        } catch (ResourceAccessException e) {
            log.warn("Executor 不可达 appCode={} url={}", appCode, url);
            return "{\"code\":500,\"message\":\"执行器不可达\"}";
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // Executor 返回 4xx，透传响应体
            String respBody = e.getResponseBodyAsString();
            if (respBody != null && !respBody.isBlank()) return respBody;
            return "{\"code\":500,\"message\":\"执行器请求失败\"}";
        } catch (Exception e) {
            log.error("代理请求失败 appCode={} url={}", appCode, url, e);
            return "{\"code\":500,\"message\":\"代理请求失败\"}";
        }
    }

    /**
     * 通过 appCode 查找可用的 Executor 地址
     *
     * @return URL 基础地址，如 http://192.168.1.10:9999
     */
    public String resolveExecutorBaseUrl(String appCode) {
        if (appCode == null || appCode.isBlank()) {
            log.warn("appCode 为空");
            return null;
        }
        List<ExecutorRegistryPO> executors = executorRegistryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExecutorRegistryPO>()
                        .eq(ExecutorRegistryPO::getAppCode, appCode)
                        .eq(ExecutorRegistryPO::getStatus, 1) // 只选在线
                        .last("LIMIT 1"));
        if (executors.isEmpty()) {
            log.warn("应用无可用执行器 appCode={}", appCode);
            return null;
        }
        ExecutorRegistryPO executor = executors.get(0);
        return protocol + "://" + executor.getExecutorHost() + ":" + executor.getExecutorPort();
    }

    private String resolveExecutorUrl(String appCode, String path, String query) {
        String baseUrl = resolveExecutorBaseUrl(appCode);
        if (baseUrl == null) return null;
        return baseUrl + path + (query != null ? query : "");
    }

    /**
     * 给分页 JSON 的每条记录补充 appCode 和 executorSource 字段
     */
    private String enrichWithAppCode(String json, String appCode) {
        return enrichWithAppCode(json, appCode, null);
    }

    private String enrichWithAppCode(String json, String appCode, String executorSource) {
        if (json == null) return emptyPage();
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root.has("records")) {
                enrichRecords(root.get("records"), appCode, executorSource);
                return MAPPER.writeValueAsString(root);
            }
            // 单条记录
            if (root.isObject() && !root.has("records")) {
                if (appCode != null) {
                    ((ObjectNode) root).put("appCode", appCode);
                }
                if (executorSource != null) {
                    ((ObjectNode) root).put("executorSource", executorSource);
                }
                String now = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                ((ObjectNode) root).put("cachedAt", now);
                // 同时注入 id=code 做前端兼容（如果前端需要数字 id）
                if (root.has("code") && !root.has("id")) {
                    ((ObjectNode) root).put("id", root.get("code").asText());
                }
                return MAPPER.writeValueAsString(root);
            }
            return json;
        } catch (Exception e) {
            log.warn("JSON 补充 appCode 失败", e);
            return json;
        }
    }

    private void enrichRecords(JsonNode records, String appCode) {
        enrichRecords(records, appCode, null);
    }

    private void enrichRecords(JsonNode records, String appCode, String executorSource) {
        if (records == null || !records.isArray()) return;
        String now = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        for (JsonNode record : records) {
            if (record.isObject()) {
                if (appCode != null) {
                    ((ObjectNode) record).put("appCode", appCode);
                }
                if (executorSource != null) {
                    ((ObjectNode) record).put("executorSource", executorSource);
                }
                ((ObjectNode) record).put("cachedAt", now);
                if (record.has("code") && !record.has("id")) {
                    ((ObjectNode) record).put("id", record.get("code").asText());
                }
            }
        }
    }

    private String emptyPage() {
        return "{\"records\":[],\"total\":0,\"current\":1,\"size\":10}";
    }

    // ==================== 广播机制 ====================

    @Data
    @AllArgsConstructor
    public static class ExecutorResult {
        private String url;
        private boolean ok;
        private String message;
    }

    @Data
    @AllArgsConstructor
    public static class BroadcastResult {
        private int total;
        private int success;
        private boolean allSuccess;
        private List<ExecutorResult> results;

        public static BroadcastResult of(int total, int success, List<ExecutorResult> results) {
            return new BroadcastResult(total, success, total == success, results);
        }

        public static BroadcastResult fail(String message) {
            ExecutorResult err = new ExecutorResult("N/A", false, message);
            return new BroadcastResult(0, 0, false, List.of(err));
        }
    }

    /**
     * 获取应用下所有在线执行器的 HTTP 基础地址
     */
    public List<String> resolveAllExecutorUrls(String appCode) {
        if (appCode == null || appCode.isBlank()) {
            log.warn("appCode 为空");
            return List.of();
        }
        List<ExecutorRegistryPO> executors = executorRegistryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExecutorRegistryPO>()
                        .eq(ExecutorRegistryPO::getAppCode, appCode)
                        .eq(ExecutorRegistryPO::getStatus, 1));
        return executors.stream()
                .map(e -> protocol + "://" + e.getExecutorHost() + ":" + e.getExecutorPort())
                .collect(Collectors.toList());
    }

    /**
     * 向应用下所有在线执行器广播请求，并行调用，单执行器超时 30s，不阻塞其他执行器。
     * <p>
     * 使用 CompletableFuture.supplyAsync 发起并行请求，每个任务独立超时，
     * 所有请求完成后汇总结果。一个执行器超时或失败不影响其他执行器。
     */
    public BroadcastResult broadcastToExecutors(String appCode, String method, String path, String body) {
        List<String> urls = resolveAllExecutorUrls(appCode);
        if (urls.isEmpty()) {
            log.warn("广播失败：应用下无在线执行器 appCode={}", appCode);
            return BroadcastResult.fail("该应用无可用执行器");
        }

        int total = urls.size();
        List<CompletableFuture<ExecutorResult>> futures = new ArrayList<>();

        for (String baseUrl : urls) {
            CompletableFuture<ExecutorResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    String fullUrl = baseUrl + path;
                    log.info("广播请求 {} {} {}", method, fullUrl, body != null ? body : "");
                    String json;
                    switch (method.toUpperCase()) {
                        case "PUT":
                            json = restTemplate.exchange(
                                    org.springframework.http.RequestEntity
                                            .put(new java.net.URI(fullUrl))
                                            .body(body != null ? body : "{}"),
                                    String.class).getBody();
                            break;
                        case "POST":
                            json = restTemplate.postForObject(fullUrl, body != null ? body : "{}", String.class);
                            break;
                        default:
                            return new ExecutorResult(baseUrl, false, "不支持的方法: " + method);
                    }
                    if (json == null) {
                        return new ExecutorResult(baseUrl, false, "响应为空");
                    }
                    JsonNode node = MAPPER.readTree(json);
                    boolean ok = (node.has("code") && node.get("code").asInt() == 200)
                            || (node.has("success") && node.get("success").asBoolean());
                    return new ExecutorResult(baseUrl, ok,
                            node.has("message") ? node.get("message").asText() : (ok ? "OK" : "FAIL"));
                } catch (ResourceAccessException e) {
                    log.warn("广播执行器不可达 url={}", baseUrl);
                    return new ExecutorResult(baseUrl, false, "执行器不可达");
                } catch (Exception e) {
                    log.error("广播请求失败 url={}", baseUrl, e);
                    return new ExecutorResult(baseUrl, false, e.getMessage());
                }
            }).orTimeout(30, TimeUnit.SECONDS)
              .exceptionally(e -> {
                  log.warn("广播执行器超时或异常 url={} err={}", baseUrl, e.getMessage());
                  return new ExecutorResult(baseUrl, false, "超时或异常: " + e.getMessage());
              });
            futures.add(future);
        }

        // 等待所有并行任务完成（每个任务自身已有 30s 超时，整体不用额外超时）
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<ExecutorResult> results = futures.stream()
                .map(f -> f.getNow(null))
                .collect(Collectors.toList());

        long successCount = results.stream().filter(ExecutorResult::isOk).count();
        log.info("广播完成 total={} success={}", total, successCount);
        return BroadcastResult.of(total, (int) successCount, results);
    }
}
