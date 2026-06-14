package com.zestflow.executor.fallback;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.executor.chain.NodeDefinition;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * 默认降级策略：按节点 {@code fallback.mode} 执行 default / constant / propagate。
 */
@Slf4j
public class DefaultFallbackStrategy implements FallbackStrategy {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public Object fallback(NodeDefinition nodeDef, ChainContext context, Throwable cause) {
        String mode = StringUtils.hasText(nodeDef.getFallbackMode())
                ? nodeDef.getFallbackMode().trim()
                : "default";
        return switch (mode) {
            case "constant" -> applyConstant(nodeDef, context);
            case "propagate" -> propagate(cause);
            default -> logAndReturnNull(nodeDef, cause);
        };
    }

    private Object logAndReturnNull(NodeDefinition nodeDef, Throwable cause) {
        log.warn("节点执行降级 nodeId={} component={} error={}",
                nodeDef.getId(), nodeDef.getComponent(), cause != null ? cause.getMessage() : "unknown");
        return null;
    }

    private Object applyConstant(NodeDefinition nodeDef, ChainContext context) {
        Object value = parseConstant(nodeDef.getFallbackConstant());
        context.put(nodeDef.getId(), value);
        log.info("节点降级常量写入上下文 nodeId={} constant={}", nodeDef.getId(), nodeDef.getFallbackConstant());
        return value;
    }

    static Object parseConstant(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")
                || trimmed.startsWith("\"") || "true".equals(trimmed) || "false".equals(trimmed)
                || trimmed.matches("-?\\d+(\\.\\d+)?")) {
            try {
                return JSON.readValue(trimmed, Object.class);
            } catch (JsonProcessingException ignored) {
                // 非 JSON 字面量则按字符串落库
            }
        }
        return raw;
    }

    private Object propagate(Throwable cause) {
        if (cause instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        throw new RuntimeException(cause != null ? cause.getMessage() : "fallback propagate without cause", cause);
    }
}
