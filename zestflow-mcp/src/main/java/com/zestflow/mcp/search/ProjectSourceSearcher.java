package com.zestflow.mcp.search;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 在 project 目录内搜索源码（只读）。
 */
public class ProjectSourceSearcher {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> SKIP_DIR_NAMES = Set.of(
            ".git", "target", "node_modules", "dist", "build", ".idea", ".gradle", "out");
    private static final long MAX_FILE_BYTES = 256 * 1024;

    public String search(Path projectRoot, String keyword, String glob, int maxResults) throws IOException {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("keyword 不能为空");
        }
        int limit = maxResults > 0 ? Math.min(maxResults, 50) : 20;
        String pattern = glob == null || glob.isBlank() ? "**/*.java" : glob.trim();
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);

        List<Map<String, Object>> matches = new ArrayList<>();
        Files.walkFileTree(projectRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (shouldSkipDir(projectRoot, dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (matches.size() >= limit) {
                    return FileVisitResult.TERMINATE;
                }
                if (!matchesGlob(projectRoot.relativize(file).toString().replace('\\', '/'), pattern)) {
                    return FileVisitResult.CONTINUE;
                }
                if (attrs.size() > MAX_FILE_BYTES) {
                    return FileVisitResult.CONTINUE;
                }
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).toLowerCase(Locale.ROOT).contains(lowerKeyword)) {
                        Map<String, Object> hit = new LinkedHashMap<>();
                        hit.put("relativePath", projectRoot.relativize(file).toString().replace('\\', '/'));
                        hit.put("lineNumber", i + 1);
                        hit.put("snippet", truncate(lines.get(i).trim(), 240));
                        matches.add(hit);
                        break;
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("keyword", keyword);
        out.put("glob", pattern);
        out.put("total", matches.size());
        out.put("matches", matches);
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(out);
    }

    static boolean matchesGlob(String relativePath, String glob) {
        String normalized = relativePath.replace('\\', '/');
        if ("**/*".equals(glob) || "**/*.*".equals(glob)) {
            return true;
        }
        if (glob.startsWith("**/")) {
            String suffix = glob.substring(3);
            if (suffix.startsWith("*.")) {
                return normalized.endsWith(suffix.substring(1));
            }
            return normalized.contains(suffix);
        }
        if (glob.startsWith("*.")) {
            return normalized.endsWith(glob.substring(1));
        }
        return normalized.equals(glob) || normalized.endsWith("/" + glob);
    }

    private static boolean shouldSkipDir(Path projectRoot, Path dir) {
        if (dir.equals(projectRoot)) {
            return false;
        }
        Path name = dir.getFileName();
        return name != null && SKIP_DIR_NAMES.contains(name.toString());
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
