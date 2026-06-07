package com.zestflow.mcp.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.Set;

/**
 * 从 list_components API 响应解析已注册 componentId 集合。
 */
public final class ComponentRegistryParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ComponentRegistryParser() {
    }

    public static Set<String> parseIds(String componentsJson) {
        Set<String> ids = new HashSet<>();
        if (componentsJson == null || componentsJson.isBlank()) {
            return ids;
        }
        try {
            JsonNode root = MAPPER.readTree(componentsJson);
            collectFromNode(root, ids);
        } catch (Exception ignored) {
            // fallback: empty
        }
        return ids;
    }

    private static void collectFromNode(JsonNode node, Set<String> ids) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectFromNode(item, ids);
            }
            return;
        }
        if (node.isObject()) {
            addIdField(node, "executeId", ids);
            addIdField(node, "componentId", ids);
            addIdField(node, "id", ids);
            if (node.has("data")) {
                collectFromNode(node.get("data"), ids);
            }
            if (node.has("records")) {
                collectFromNode(node.get("records"), ids);
            }
            if (node.has("list")) {
                collectFromNode(node.get("list"), ids);
            }
            if (node.has("content")) {
                collectFromNode(node.get("content"), ids);
            }
        }
    }

    private static void addIdField(JsonNode node, String field, Set<String> ids) {
        if (node.hasNonNull(field)) {
            String v = node.get(field).asText("").trim();
            if (!v.isBlank() && !v.matches("\\d+")) {
                ids.add(v);
            }
        }
    }
}
