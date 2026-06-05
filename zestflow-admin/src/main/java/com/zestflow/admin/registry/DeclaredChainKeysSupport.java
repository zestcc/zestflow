package com.zestflow.admin.registry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * executor_registry.declared_chain_keys JSON 序列化辅助。
 */
public final class DeclaredChainKeysSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {};

    private DeclaredChainKeysSupport() {
    }

    public static String toJson(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(keys);
        } catch (Exception e) {
            return null;
        }
    }

    public static List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<String> list = MAPPER.readValue(json, LIST_TYPE);
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public static Set<String> union(List<String>... lists) {
        Set<String> merged = new LinkedHashSet<>();
        if (lists == null) {
            return merged;
        }
        for (List<String> list : lists) {
            if (list == null) {
                continue;
            }
            for (String key : list) {
                if (key != null && !key.isBlank()) {
                    merged.add(key.trim());
                }
            }
        }
        return merged;
    }

    public static List<String> normalize(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String key : keys) {
            if (key != null && !key.isBlank()) {
                normalized.add(key.trim());
            }
        }
        return new ArrayList<>(normalized);
    }
}
