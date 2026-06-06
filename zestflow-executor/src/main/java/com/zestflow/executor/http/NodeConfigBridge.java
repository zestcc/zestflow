package com.zestflow.executor.http;

import com.zestflow.executor.chain.NodeDefinition;
import com.zestflow.executor.context.ChainContext;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 设计器节点 config → 执行上下文桥接（HTTP / 缓存 / MQ 等集成字段）。
 */
public final class NodeConfigBridge {

    private static final Pattern TEMPLATE = Pattern.compile("\\$\\{([^}]+)}");

    private NodeConfigBridge() {
    }

    public static void apply(NodeDefinition nodeDef, ChainContext context) {
        Map<String, Object> cfg = nodeDef.getConfig();
        if (cfg == null || cfg.isEmpty()) {
            return;
        }
        context.put("_nodeConfig", cfg);
        bridgeHttp(cfg, context);
        bridgeCache(cfg, context);
        cfg.forEach((k, v) -> {
            if (v != null && context.get(k) == null) {
                context.put(k, v);
            }
        });
    }

    private static void bridgeHttp(Map<String, Object> cfg, ChainContext context) {
        if (cfg.containsKey("httpUrl") && context.get("_http_url") == null) {
            context.put("_http_url", expandTemplate(String.valueOf(cfg.get("httpUrl")), context));
        }
        if (cfg.containsKey("httpBodyTemplate") && context.get("_http_body") == null) {
            context.put("_http_body", expandTemplate(String.valueOf(cfg.get("httpBodyTemplate")), context));
        }
        if (cfg.containsKey("httpMethod") && context.get("_http_method") == null) {
            context.put("_http_method", String.valueOf(cfg.get("httpMethod")));
        }
    }

    private static void bridgeCache(Map<String, Object> cfg, ChainContext context) {
        if (cfg.containsKey("cacheKey") && context.get("cacheKey") == null) {
            context.put("cacheKey", expandTemplate(String.valueOf(cfg.get("cacheKey")), context));
        }
    }

    static String expandTemplate(String template, ChainContext context) {
        if (template == null || template.isBlank()) {
            return template;
        }
        Matcher matcher = TEMPLATE.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object val = context.get(key);
            matcher.appendReplacement(sb, val != null ? Matcher.quoteReplacement(val.toString()) : "");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
