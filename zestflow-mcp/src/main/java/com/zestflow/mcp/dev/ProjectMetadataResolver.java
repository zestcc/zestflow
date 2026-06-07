package com.zestflow.mcp.dev;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 从业务工程推断 appCode、Executor 端口与建议包名（轻量文本解析，无 YAML 依赖）。
 */
public final class ProjectMetadataResolver {

    private static final Pattern GROUP_ID = Pattern.compile("<groupId>\\s*([^<\\s]+)\\s*</groupId>");
    private static final Pattern ARTIFACT_ID = Pattern.compile("<artifactId>\\s*([^<\\s]+)\\s*</artifactId>");
    private static final Pattern KV = Pattern.compile("^([a-zA-Z0-9_-]+):\\s*(.*)$");

    private ProjectMetadataResolver() {
    }

    public static ProjectMetadata resolve(Path projectRoot) throws IOException {
        String appCode = null;
        Integer executorPort = null;

        for (Path yaml : findApplicationYmlFiles(projectRoot)) {
            String text = Files.readString(yaml, StandardCharsets.UTF_8);
            if (appCode == null) {
                appCode = readNestedValue(text, "spring", "application", "name");
            }
            if (appCode == null) {
                appCode = readNestedValue(text, "zestflow", "executor", "app-code");
            }
            if (executorPort == null) {
                String portText = readNestedValue(text, "zestflow", "executor", "port");
                executorPort = parsePort(portText);
            }
        }

        Path pom = projectRoot.resolve("pom.xml");
        String basePackage = "com.example.app";
        if (Files.isRegularFile(pom)) {
            basePackage = resolveBasePackage(Files.readString(pom, StandardCharsets.UTF_8));
        }

        if (appCode == null || appCode.isBlank()) {
            appCode = "demo";
        }
        int port = executorPort != null ? executorPort : 20550;
        String executorUrl = "http://127.0.0.1:" + port;

        return new ProjectMetadata(appCode.trim(), executorUrl, basePackage);
    }

    static String readNestedValue(String text, String... path) {
        if (text == null || text.isBlank() || path == null || path.length == 0) {
            return null;
        }
        String[] lines = text.split("\\R");
        int targetDepth = path.length;
        int[] targetIndents = new int[targetDepth];
        int matchedDepth = 0;

        for (String rawLine : lines) {
            if (rawLine.isBlank() || rawLine.trim().startsWith("#")) {
                continue;
            }
            int indent = leadingIndent(rawLine);
            String trimmed = rawLine.trim();
            Matcher kv = KV.matcher(trimmed);
            if (!kv.matches()) {
                continue;
            }
            String key = kv.group(1);
            String value = stripQuotes(kv.group(2).trim());

            while (matchedDepth > 0 && indent <= targetIndents[matchedDepth - 1]) {
                matchedDepth--;
            }
            if (matchedDepth >= targetDepth) {
                matchedDepth = 0;
            }
            if (!key.equals(path[matchedDepth])) {
                if (matchedDepth == 0 && key.equals(path[0])) {
                    targetIndents[0] = indent;
                    matchedDepth = 1;
                    if (targetDepth == 1) {
                        return normalizeScalar(value);
                    }
                }
                continue;
            }

            targetIndents[matchedDepth] = indent;
            matchedDepth++;
            if (matchedDepth == targetDepth) {
                return normalizeScalar(value);
            }
        }
        return null;
    }

    private static List<Path> findApplicationYmlFiles(Path projectRoot) throws IOException {
        Path resources = projectRoot.resolve("src/main/resources");
        if (!Files.isDirectory(resources)) {
            return List.of();
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(resources)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.startsWith("application") && (name.endsWith(".yml") || name.endsWith(".yaml"));
                    })
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(files::add);
        }
        return files;
    }

    static String resolveBasePackage(String pomText) {
        String withoutParent = pomText.replaceFirst("(?s)<parent>.*?</parent>", "");
        String artifactId = firstMatch(ARTIFACT_ID, withoutParent);
        String groupId = firstMatch(GROUP_ID, withoutParent);
        if (groupId == null) {
            groupId = firstMatch(GROUP_ID, pomText);
        }
        if (groupId != null && artifactId != null) {
            return toBasePackage(groupId, artifactId);
        }
        return "com.example.app";
    }

    static String toBasePackage(String groupId, String artifactId) {
        String group = groupId.trim().replace('-', '.');
        String artifact = artifactId.trim().replace('-', '.');
        if (group.endsWith("." + artifact)) {
            return group;
        }
        return group + "." + artifact;
    }

    private static int leadingIndent(String line) {
        int count = 0;
        while (count < line.length()) {
            char ch = line.charAt(count);
            if (ch == ' ') {
                count++;
            } else if (ch == '\t') {
                count += 4;
            } else {
                break;
            }
        }
        return count;
    }

    private static String stripQuotes(String value) {
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String normalizeScalar(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        return value.trim();
    }

    private static Integer parsePort(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String firstMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1).trim();
    }
}
