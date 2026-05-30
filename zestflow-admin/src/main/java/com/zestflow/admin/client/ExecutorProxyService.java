package com.zestflow.admin.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.model.entity.ModulePO;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.admin.repository.ModuleMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
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
    private final ModuleMapper moduleMapper;
    private final ExecutorRegistryMapper executorRegistryMapper;

    /**
     * 通过 moduleId 解析到 Executor 地址并执行 GET 请求
     *
     * @param moduleId 模块 ID
     * @param path     Executor API 路径，如 /api/chains
     * @param query    查询参数字符串（含 ?），如 "?keyword=xx&status=1&page=1&size=10"
     * @return 等价的空数据 JSON（executor 不可达时）
     */
    public String getFromExecutor(Long moduleId, String path, String query) {
        String baseUrl = resolveExecutorBaseUrl(moduleId);
        if (baseUrl == null) {
            return emptyPage();
        }
        String url = baseUrl + path + (query != null ? query : "");
        try {
            String json = restTemplate.getForObject(url, String.class);
            if (json == null) return emptyPage();
            String executorSource = baseUrl.replace("http://", "");
            return enrichWithModuleId(json, moduleId, executorSource);
        } catch (ResourceAccessException e) {
            log.warn("Executor 不可达 moduleId={} url={}", moduleId, url);
            return emptyPage();
        } catch (Exception e) {
            log.error("代理 GET 请求失败 moduleId={} url={}", moduleId, url, e);
            return emptyPage();
        }
    }

    /**
     * 通过 moduleId 解析到 Executor 地址并执行 GET 请求（不分页，返回数组）
     */
    public String getArrayFromExecutor(Long moduleId, String path, String query) {
        String url = resolveExecutorUrl(moduleId, path, query);
        if (url == null) {
            return "[]";
        }
        try {
            String json = restTemplate.getForObject(url, String.class);
            if (json == null) return "[]";
            // 尝试解析为分页格式，提取 records
            JsonNode root = MAPPER.readTree(json);
            if (root.has("records")) {
                enrichRecords(root.get("records"), moduleId);
                return MAPPER.writeValueAsString(root.get("records"));
            }
            return json;
        } catch (ResourceAccessException e) {
            log.warn("Executor 不可达 moduleId={}", moduleId);
            return "[]";
        } catch (Exception e) {
            log.error("代理 GET 请求失败 moduleId={}", moduleId, e);
            return "[]";
        }
    }

    /**
     * 通过 Executor 地址直接 GET（不经过 moduleId 解析）
     */
    public String getDirect(String host, int port, String path, String query) {
        try {
            String url = "http://" + host + ":" + port + path + (query != null ? query : "");
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
     * 通过 moduleId 解析到 Executor 并执行 POST/PUT/DELETE
     */
    public String executeOnExecutor(Long moduleId, String method, String path, String body) {
        String url = resolveExecutorUrl(moduleId, path, null);
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
            return enrichWithModuleId(json, moduleId);
        } catch (ResourceAccessException e) {
            log.warn("Executor 不可达 moduleId={} url={}", moduleId, url);
            return "{\"code\":500,\"message\":\"执行器不可达\"}";
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // Executor 返回 4xx，透传响应体
            String respBody = e.getResponseBodyAsString();
            if (respBody != null && !respBody.isBlank()) return respBody;
            return "{\"code\":500,\"message\":\"" + e.getStatusCode().value() + " " + e.getMessage() + "\"}";
        } catch (Exception e) {
            log.error("代理请求失败 moduleId={} url={}", moduleId, url, e);
            return "{\"code\":500,\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    /**
     * 通过 moduleId 查找可用的 Executor 地址
     *
     * @return URL 基础地址，如 http://192.168.1.10:9999
     */
    public String resolveExecutorBaseUrl(Long moduleId) {
        ModulePO module = moduleMapper.selectById(moduleId);
        if (module == null) {
            log.warn("模块不存在 moduleId={}", moduleId);
            return null;
        }
        List<ExecutorRegistryPO> executors = executorRegistryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExecutorRegistryPO>()
                        .eq(ExecutorRegistryPO::getModuleId, moduleId)
                        .eq(ExecutorRegistryPO::getStatus, 1) // 只选在线
                        .last("LIMIT 1"));
        if (executors.isEmpty()) {
            log.warn("模块无可用执行器 moduleId={}", moduleId);
            return null;
        }
        ExecutorRegistryPO executor = executors.get(0);
        return "http://" + executor.getExecutorHost() + ":" + executor.getExecutorPort();
    }

    private String resolveExecutorUrl(Long moduleId, String path, String query) {
        String baseUrl = resolveExecutorBaseUrl(moduleId);
        if (baseUrl == null) return null;
        return baseUrl + path + (query != null ? query : "");
    }

    /**
     * 给分页 JSON 的每条记录补充 moduleId 字段
     */
    private String enrichWithModuleId(String json, Long moduleId) {
        return enrichWithModuleId(json, moduleId, null);
    }

    private String enrichWithModuleId(String json, Long moduleId, String executorSource) {
        if (json == null) return emptyPage();
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root.has("records")) {
                enrichRecords(root.get("records"), moduleId, executorSource);
                return MAPPER.writeValueAsString(root);
            }
            // 单条记录
            if (root.isObject() && !root.has("records")) {
                ((ObjectNode) root).put("moduleId", moduleId);
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
            log.warn("JSON 补充 moduleId 失败", e);
            return json;
        }
    }

    private void enrichRecords(JsonNode records, Long moduleId) {
        enrichRecords(records, moduleId, null);
    }

    private void enrichRecords(JsonNode records, Long moduleId, String executorSource) {
        if (records == null || !records.isArray()) return;
        String now = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        for (JsonNode record : records) {
            if (record.isObject()) {
                ((ObjectNode) record).put("moduleId", moduleId);
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
     * 获取模块下所有在线执行器的 HTTP 基础地址
     */
    public List<String> resolveAllExecutorUrls(Long moduleId) {
        ModulePO module = moduleMapper.selectById(moduleId);
        if (module == null) {
            log.warn("模块不存在 moduleId={}", moduleId);
            return List.of();
        }
        List<ExecutorRegistryPO> executors = executorRegistryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExecutorRegistryPO>()
                        .eq(ExecutorRegistryPO::getModuleId, moduleId)
                        .eq(ExecutorRegistryPO::getStatus, 1));
        return executors.stream()
                .map(e -> "http://" + e.getExecutorHost() + ":" + e.getExecutorPort())
                .collect(Collectors.toList());
    }

    /**
     * 向模块下所有在线执行器广播请求，收集每个执行器的响应结果。
     * <p>
     * 顺序调用（非并行），每次调用为一次完整的 HTTP 请求-响应周期。
     * 后续可升级为 CompletableFuture 超时并发。
     */
    public BroadcastResult broadcastToExecutors(Long moduleId, String method, String path, String body) {
        List<String> urls = resolveAllExecutorUrls(moduleId);
        if (urls.isEmpty()) {
            log.warn("广播失败：模块下无在线执行器 moduleId={}", moduleId);
            return BroadcastResult.fail("该模块无可用执行器");
        }

        int total = urls.size();
        List<ExecutorResult> results = new ArrayList<>();

        for (String baseUrl : urls) {
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
                        results.add(new ExecutorResult(baseUrl, false, "不支持的方法: " + method));
                        continue;
                }
                JsonNode node = MAPPER.readTree(json);
                // 兼容两种响应格式：{"code":200,...} 或 PublishEventDTO {"success":true,...}
                boolean ok = (node.has("code") && node.get("code").asInt() == 200)
                        || (node.has("success") && node.get("success").asBoolean());
                results.add(new ExecutorResult(baseUrl, ok,
                        node.has("message") ? node.get("message").asText() : (ok ? "OK" : "FAIL")));
            } catch (ResourceAccessException e) {
                log.warn("广播执行器不可达 url={}", baseUrl);
                results.add(new ExecutorResult(baseUrl, false, "执行器不可达"));
            } catch (Exception e) {
                log.error("广播请求失败 url={}", baseUrl, e);
                results.add(new ExecutorResult(baseUrl, false, e.getMessage()));
            }
        }

        long successCount = results.stream().filter(ExecutorResult::isOk).count();
        log.info("广播完成 total={} success={}", total, successCount);
        return BroadcastResult.of(total, (int) successCount, results);
    }
}
