package com.zestflow.admin.client.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

/**
 * 读快照 JSON 元数据注入。
 */
@Slf4j
public final class ExecutorReadCacheJsonSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ExecutorReadCacheJsonSupport() {
    }

    public static String attachReadCacheMeta(String json, long cachedAtMs) {
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root.isObject()) {
                ObjectNode obj = (ObjectNode) root;
                ObjectNode meta = MAPPER.createObjectNode();
                meta.put("stale", true);
                meta.put("cachedAt", cachedAtMs);
                obj.set("_readCache", meta);
                return MAPPER.writeValueAsString(obj);
            }
        } catch (Exception ex) {
            log.debug("读快照 meta 注入失败", ex);
        }
        return json;
    }

    public static boolean shouldSkipCache(String json) {
        if (json == null || json.isBlank()) {
            return true;
        }
        return json.contains("\"code\":404") || json.contains("\"code\": 404");
    }
}
