package com.zestflow.mcp.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;

/**
 * MCP Tool 调用审计（JSONL，默认写入 project/.zestflow/mcp-audit.jsonl）。
 */
public class McpAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(McpAuditLogger.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path auditLogPath;
    private final boolean enabled;

    public McpAuditLogger(Path auditLogPath, boolean enabled) {
        this.auditLogPath = auditLogPath;
        this.enabled = enabled;
    }

    public void logToolCall(String toolName, Map<String, Object> arguments, boolean success,
                            boolean isError, long durationMs, String message) {
        if (!enabled) {
            return;
        }
        try {
            ObjectNode entry = MAPPER.createObjectNode();
            entry.put("ts", Instant.now().toString());
            entry.put("tool", toolName);
            entry.put("success", success);
            entry.put("isError", isError);
            entry.put("durationMs", durationMs);
            if (message != null && !message.isBlank()) {
                entry.put("message", truncate(message, 200));
            }
            if (arguments != null && !arguments.isEmpty()) {
                ObjectNode argsNode = entry.putObject("args");
                arguments.forEach((k, v) -> {
                    if (v != null && !isSensitive(k)) {
                        argsNode.put(k, truncate(String.valueOf(v), 120));
                    }
                });
            }
            String line = MAPPER.writeValueAsString(entry);
            if (auditLogPath.getParent() != null) {
                Files.createDirectories(auditLogPath.getParent());
            }
            Files.writeString(auditLogPath, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.warn("Failed to write MCP audit log: {}", e.getMessage());
        }
    }

    private static boolean isSensitive(String key) {
        String lower = key.toLowerCase();
        return lower.contains("token") || lower.contains("password") || lower.contains("secret");
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
