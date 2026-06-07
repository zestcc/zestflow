package com.zestflow.mcp.dev;

import java.util.Locale;
import java.util.Set;

/**
 * {@code --init-dev} 选项。
 */
public record DevInitOptions(
        String appCode,
        String executorUrl,
        String basePackage,
        Set<IdeTarget> ides,
        boolean force,
        boolean appendGitignore) {

    public enum IdeTarget {
        CURSOR,
        VSCODE,
        CLAUDE;

        public static IdeTarget parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return CURSOR;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "cursor" -> CURSOR;
                case "vscode", "vs-code" -> VSCODE;
                case "claude", "claude-desktop" -> CLAUDE;
                case "all" -> throw new IllegalArgumentException("use parseAll for 'all'");
                default -> throw new IllegalArgumentException("未知 --ide: " + raw + "（可选 cursor|vscode|claude|all）");
            };
        }

        public static Set<IdeTarget> parseAll(String raw) {
            if (raw == null || raw.isBlank() || "all".equalsIgnoreCase(raw)) {
                return Set.of(CURSOR, VSCODE, CLAUDE);
            }
            return Set.of(parse(raw));
        }
    }
}
