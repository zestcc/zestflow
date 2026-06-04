package com.zestflow.executor.scanner;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 元件 ID 冲突异常：同一 value 被多个方法注册时抛出，携带冲突方法与完整类路径。
 */
public class ComponentIdConflictException extends RuntimeException {

    private final Map<String, List<String>> conflicts;

    public ComponentIdConflictException(Map<String, List<String>> conflicts) {
        super(buildMessage(conflicts));
        this.conflicts = conflicts;
    }

    public Map<String, List<String>> getConflicts() {
        return conflicts;
    }

    private static String buildMessage(Map<String, List<String>> conflicts) {
        String details = conflicts.entrySet().stream()
                .map(e -> "value=\"" + e.getKey() + "\" → " + String.join(", ", e.getValue()))
                .collect(Collectors.joining("; "));
        return "执行元件 ID 冲突: " + details;
    }
}
