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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
     * Admin → Executor Netty 机器鉴权令牌（与 {@code zestflow.executor.access-token} 一致，非用户 JWT）
     */
    @Value("${zestflow.admin.executor-access-token:}")
    private String executorAccessToken;

    /** 读请求 fan-out 线程池 */
    private final ExecutorService readExecutor = Executors.newFixedThreadPool(
            Math.min(8, Math.max(2, Runtime.getRuntime().availableProcessors())),
            r -> {
                Thread t = new Thread(r, "zestflow-executor-read");
                t.setDaemon(true);
                return t;
            });

    /** 广播专用线程池，避免占用 ForkJoinPool.commonPool */
    private final ExecutorService broadcastExecutor = Executors.newFixedThreadPool(
            Math.min(8, Math.max(2, Runtime.getRuntime().availableProcessors())),
            r -> {
                Thread t = new Thread(r, "zestflow-executor-broadcast");
                t.setDaemon(true);
                return t;
            });

    /**
     * 通过 appCode 解析到 Executor 地址并执行 GET 请求
     *
     * @param appCode 应用编码
     * @param path    Executor API 路径，如 /api/chains
     * @param query   查询参数字符串（含 ?），如 "?keyword=xx&status=1&page=1&size=10"
     * @return 等价的空数据 JSON（executor 不可达时）
     */
    public String getFromExecutor(String appCode, String path, String query) {
        List<ExecutorRegistryPO> executors = findOnlineExecutors(appCode);
        if (executors.isEmpty()) {
            return emptyPage();
        }
        if (executors.size() == 1 || !isMergeableListPath(path)) {
            return fetchFromExecutor(selectPrimary(executors), path, query, appCode);
        }
        return mergePagedFromExecutors(executors, path, query, appCode);
    }

    private String fetchFromExecutor(ExecutorRegistryPO executor, String path, String query, String appCode) {
        String baseUrl = toBaseUrl(executor);
        String url = baseUrl + path + (query != null ? query : "");
        try {
            HttpEntity<Void> entity = new HttpEntity<>(executorHeaders());
            String json = restTemplate.exchange(url, HttpMethod.GET, entity, String.class).getBody();
            if (json == null) {
                return emptyPage();
            }
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
        List<ExecutorRegistryPO> executors = findOnlineExecutors(appCode);
        if (executors.isEmpty()) {
            return "[]";
        }
        if (executors.size() == 1) {
            return fetchArrayFromExecutor(selectPrimary(executors), path, query, appCode);
        }
        return mergeArrayFromExecutors(executors, path, query, appCode);
    }

    private String fetchArrayFromExecutor(ExecutorRegistryPO executor, String path, String query, String appCode) {
        String baseUrl = toBaseUrl(executor);
        String url = baseUrl + path + (query != null ? query : "");
        try {
            HttpEntity<Void> entity = new HttpEntity<>(executorHeaders());
            String json = restTemplate.exchange(url, HttpMethod.GET, entity, String.class).getBody();
            if (json == null) {
                return "[]";
            }
            JsonNode root = MAPPER.readTree(json);
            if (root.has("records")) {
                enrichRecords(root.get("records"), appCode);
                return MAPPER.writeValueAsString(root.get("records"));
            }
            if (root.isArray()) {
                enrichRecords(root, appCode);
                return MAPPER.writeValueAsString(root);
            }
            return json;
        } catch (ResourceAccessException e) {
            log.warn("Executor 不可达 appCode={}", appCode);
            return "[]";
        } catch (Exception e) {
            log.error("代理 GET 数组请求失败 appCode={}", appCode, e);
            return "[]";
        }
    }

    /**
     * 通过 Executor 地址直接 GET（不经过 appCode 解析）
     */
    public String getDirect(String host, int port, String path, String query) {
        try {
            String url = protocol + "://" + host + ":" + port + path + (query != null ? query : "");
            HttpEntity<Void> entity = new HttpEntity<>(executorHeaders());
            return restTemplate.exchange(url, HttpMethod.GET, entity, String.class).getBody();
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
            HttpEntity<Void> entity = new HttpEntity<>(executorHeaders());
            return restTemplate.exchange(fullUrl, HttpMethod.GET, entity, String.class).getBody();
        } catch (ResourceAccessException e) {
            log.warn("Executor 不可达 url={}", url);
            return emptyPage();
        } catch (Exception e) {
            log.error("代理 GET 请求失败 url={}", url, e);
            return emptyPage();
        }
    }

    /**
     * 通过 appCode 解析到 Executor 并执行 POST/PUT/DELETE（多实例时广播）或 GET（单实例轮询）
     */
    public String executeOnExecutor(String appCode, String method, String path, String body) {
        String upper = method.toUpperCase();
        if ("POST".equals(upper) || "PUT".equals(upper) || "DELETE".equals(upper)) {
            List<String> urls = resolveAllExecutorUrls(appCode);
            if (urls.isEmpty()) {
                return "{\"code\":500,\"message\":\"无可用执行器\"}";
            }
            if (urls.size() == 1) {
                ExecutorResult result = executeOnExecutorUrl(urls.get(0), upper, path, body);
                if (result.isOk()) {
                    return enrichWithAppCode(
                            result.getResponseBody() != null ? result.getResponseBody() : "{\"code\":200}",
                            appCode);
                }
                return result.getResponseBody() != null ? result.getResponseBody()
                        : "{\"code\":500,\"message\":\"" + escapeJson(result.getMessage()) + "\"}";
            }
            BroadcastResult broadcast = broadcastToExecutors(appCode, upper, path, body);
            if (broadcast.isAllSuccess()) {
                String firstBody = broadcast.getResults().stream()
                        .filter(ExecutorResult::isOk)
                        .map(ExecutorResult::getResponseBody)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse("{\"code\":200,\"message\":\"success\"}");
                return enrichWithAppCode(firstBody, appCode);
            }
            return String.format("{\"code\":207,\"message\":\"部分执行器操作失败 success=%d/%d\"}",
                    broadcast.getSuccess(), broadcast.getTotal());
        }

        String baseUrl = resolveExecutorBaseUrl(appCode);
        if (baseUrl == null) {
            return "{\"code\":500,\"message\":\"无可用执行器\"}";
        }
        String url = baseUrl + path;
        try {
            String json = restTemplate.exchange(
                    RequestEntity.get(new java.net.URI(url)).headers(executorHeaders()).build(),
                    String.class).getBody();
            return enrichWithAppCode(json, appCode);
        } catch (ResourceAccessException e) {
            log.warn("Executor 不可达 appCode={} url={}", appCode, url);
            return "{\"code\":500,\"message\":\"执行器不可达\"}";
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            String respBody = e.getResponseBodyAsString();
            if (respBody != null && !respBody.isBlank()) return respBody;
            return "{\"code\":500,\"message\":\"执行器请求失败\"}";
        } catch (Exception e) {
            log.error("代理 GET 请求失败 appCode={} url={}", appCode, url, e);
            return "{\"code\":500,\"message\":\"代理请求失败\"}";
        }
    }

    /**
     * 向指定 Executor 基础地址发送写操作（用于发布回滚等精确路由）
     */
    public ExecutorResult executeOnExecutorUrl(String baseUrl, String method, String path, String body) {
        return executeOnExecutorUrlInternal(baseUrl, method.toUpperCase(), path, body);
    }

    private ExecutorResult executeOnExecutorUrlInternal(String baseUrl, String upper, String path, String body) {
        try {
            String fullUrl = baseUrl + path;
            String json;
            switch (upper) {
                case "POST":
                    json = restTemplate.postForObject(fullUrl, new HttpEntity<>(body, executorHeaders()), String.class);
                    break;
                case "PUT":
                    json = restTemplate.exchange(
                            RequestEntity.put(new java.net.URI(fullUrl))
                                    .headers(executorHeaders())
                                    .body(body != null ? body : "{}"),
                            String.class).getBody();
                    break;
                case "DELETE":
                    json = restTemplate.exchange(
                            RequestEntity.delete(new java.net.URI(fullUrl))
                                    .headers(executorHeaders())
                                    .build(),
                            String.class).getBody();
                    break;
                default:
                    return new ExecutorResult(baseUrl, false, "不支持的方法: " + upper, null);
            }
            return parseExecutorResponse(baseUrl, json);
        } catch (ResourceAccessException e) {
            log.warn("Executor 不可达 url={}", baseUrl);
            return new ExecutorResult(baseUrl, false, "执行器不可达", null);
        } catch (Exception e) {
            log.error("代理请求失败 url={}", baseUrl, e);
            return new ExecutorResult(baseUrl, false, e.getMessage(), null);
        }
    }

    /**
     * 通过 appCode 查找主 Executor（executorId 字典序最小，读路径稳定）
     */
    public String resolveExecutorBaseUrl(String appCode) {
        List<ExecutorRegistryPO> executors = findOnlineExecutors(appCode);
        if (executors.isEmpty()) {
            log.warn("应用无可用执行器 appCode={}", appCode);
            return null;
        }
        return toBaseUrl(selectPrimary(executors));
    }

    private List<ExecutorRegistryPO> findOnlineExecutors(String appCode) {
        if (appCode == null || appCode.isBlank()) {
            return List.of();
        }
        return executorRegistryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExecutorRegistryPO>()
                        .eq(ExecutorRegistryPO::getAppCode, appCode)
                        .eq(ExecutorRegistryPO::getStatus, 1));
    }

    private ExecutorRegistryPO selectPrimary(List<ExecutorRegistryPO> executors) {
        return executors.stream()
                .sorted(Comparator.comparing(ExecutorRegistryPO::getExecutorId, Comparator.nullsLast(String::compareTo)))
                .findFirst()
                .orElse(null);
    }

    private String toBaseUrl(ExecutorRegistryPO executor) {
        return protocol + "://" + executor.getExecutorHost() + ":" + executor.getExecutorPort();
    }

    private boolean isMergeableListPath(String path) {
        return "/api/chains".equals(path) || "/api/designs".equals(path) || "/api/components".equals(path);
    }

    private String mergePagedFromExecutors(List<ExecutorRegistryPO> executors, String path, String query, String appCode) {
        PagedQueryParser.ParsedPage clientPage = PagedQueryParser.parse(query);
        String fanOutQuery = PagedQueryParser.forFanOut(query, PagedQueryParser.DEFAULT_FAN_OUT_SIZE);
        List<CompletableFuture<String>> futures = executors.stream()
                .map(executor -> CompletableFuture.supplyAsync(
                        () -> fetchFromExecutor(executor, path, fanOutQuery, appCode), readExecutor))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        Map<String, JsonNode> dedup = new LinkedHashMap<>();
        long totalHint = 0;
        for (CompletableFuture<String> future : futures) {
            try {
                JsonNode root = MAPPER.readTree(future.getNow(emptyPage()));
                if (root.has("total")) {
                    totalHint = Math.max(totalHint, root.get("total").asLong());
                }
                if (root.has("records") && root.get("records").isArray()) {
                    for (JsonNode record : root.get("records")) {
                        String key = record.has("code") ? record.get("code").asText()
                                : (record.has("id") ? record.get("id").asText() : record.toString());
                        dedup.putIfAbsent(key, record);
                    }
                }
            } catch (Exception e) {
                log.warn("合并 Executor 分页响应失败 path={}", path, e);
            }
        }
        List<JsonNode> mergedList = new ArrayList<>(dedup.values());
        mergedList.sort(Comparator.comparing(
                ExecutorProxyService::extractUpdatedAtSortKey,
                Comparator.nullsLast(Comparator.reverseOrder())));

        int from = Math.max(0, (clientPage.page() - 1) * clientPage.size());
        int to = Math.min(mergedList.size(), from + clientPage.size());
        List<JsonNode> slice = from >= mergedList.size() ? List.of() : mergedList.subList(from, to);

        ArrayNode merged = MAPPER.createArrayNode();
        slice.forEach(merged::add);
        enrichRecords(merged, appCode);
        ObjectNode pageObj = MAPPER.createObjectNode();
        pageObj.set("records", merged);
        pageObj.put("total", Math.max(mergedList.size(), totalHint));
        pageObj.put("current", clientPage.page());
        pageObj.put("size", clientPage.size());
        try {
            return MAPPER.writeValueAsString(pageObj);
        } catch (Exception e) {
            return emptyPage();
        }
    }

    private static String extractUpdatedAtSortKey(JsonNode record) {
        if (record == null || !record.isObject()) {
            return null;
        }
        if (record.has("updatedAt") && !record.get("updatedAt").isNull()) {
            return record.get("updatedAt").asText();
        }
        if (record.has("createdAt") && !record.get("createdAt").isNull()) {
            return record.get("createdAt").asText();
        }
        return null;
    }

    private String mergeArrayFromExecutors(List<ExecutorRegistryPO> executors, String path, String query, String appCode) {
        List<CompletableFuture<String>> futures = executors.stream()
                .map(executor -> CompletableFuture.supplyAsync(
                        () -> fetchArrayFromExecutor(executor, path, query, appCode), readExecutor))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        Map<String, JsonNode> dedup = new LinkedHashMap<>();
        for (CompletableFuture<String> future : futures) {
            try {
                JsonNode root = MAPPER.readTree(future.getNow("[]"));
                if (root.isArray()) {
                    for (JsonNode item : root) {
                        String key = item.has("path") ? item.get("path").asText()
                                : (item.has("code") ? item.get("code").asText()
                                : (item.has("className") ? item.get("className").asText() : item.toString()));
                        dedup.putIfAbsent(key, item);
                    }
                }
            } catch (Exception e) {
                log.warn("合并 Executor 数组响应失败 path={}", path, e);
            }
        }
        ArrayNode merged = MAPPER.createArrayNode();
        dedup.values().forEach(merged::add);
        enrichRecords(merged, appCode);
        try {
            return MAPPER.writeValueAsString(merged);
        } catch (Exception e) {
            return "[]";
        }
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

    private HttpHeaders executorHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (executorAccessToken != null && !executorAccessToken.isEmpty()) {
            headers.set("X-Access-Token", executorAccessToken);
        }
        return headers;
    }

    // ==================== 广播机制 ====================

    @Data
    @AllArgsConstructor
    public static class ExecutorResult {
        private String url;
        private boolean ok;
        private String message;
        private String responseBody;
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
            ExecutorResult err = new ExecutorResult("N/A", false, message, null);
            return new BroadcastResult(0, 0, false, List.of(err));
        }
    }

    private ExecutorResult parseExecutorResponse(String baseUrl, String json) {
        if (json == null) {
            return new ExecutorResult(baseUrl, false, "响应为空", null);
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            boolean ok = (node.has("code") && node.get("code").asInt() == 200)
                    || (node.has("success") && node.get("success").asBoolean());
            String message = node.has("message") ? node.get("message").asText() : (ok ? "OK" : "FAIL");
            return new ExecutorResult(baseUrl, ok, message, json);
        } catch (Exception e) {
            return new ExecutorResult(baseUrl, true, "OK", json);
        }
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 获取应用下所有在线执行器的 HTTP 基础地址
     */
    public List<String> resolveAllExecutorUrls(String appCode) {
        return findOnlineExecutors(appCode).stream()
                .map(this::toBaseUrl)
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
                    return executeOnExecutorUrlInternal(baseUrl, method.toUpperCase(), path, body);
                } catch (Exception e) {
                    log.error("广播请求失败 url={}", baseUrl, e);
                    return new ExecutorResult(baseUrl, false, e.getMessage(), null);
                }
            }, broadcastExecutor).orTimeout(30, TimeUnit.SECONDS)
              .exceptionally(e -> {
                  log.warn("广播执行器超时或异常 url={} err={}", baseUrl, e.getMessage());
                  return new ExecutorResult(baseUrl, false, "超时或异常: " + e.getMessage(), null);
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
