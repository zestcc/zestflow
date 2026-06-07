package com.zestflow.devinit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从业务工程推断 appCode 与 Executor 端口（轻量文本解析，无 YAML 依赖）。
 */
public final class ProjectMetadataResolver {

    private static final Pattern KV = Pattern.compile("^([a-zA-Z0-9_-]+):\\s*(.*)$");

    private ProjectMetadataResolver() {
    }

    public static ProjectMetadata resolve(Path projectRoot) throws IOException {
        String appCode = null;
        Integer executorPort = null;
        int bestScore = -1;

        for (Path yaml : findApplicationYmlFiles(projectRoot)) {
            String text = IoUtil.readFile(yaml);
            String explicitAppCode = readNestedValue(text, "zestflow", "executor", "app-code");
            String springName = readNestedValue(text, "spring", "application", "name");
            String portText = readNestedValue(text, "zestflow", "executor", "port");
            int score = scoreYaml(explicitAppCode, springName, portText);
            if (score < bestScore) {
                continue;
            }
            if (score > bestScore) {
                bestScore = score;
                appCode = firstNonBlank(explicitAppCode, springName);
                executorPort = parsePort(portText);
            } else {
                if (Strings.isBlank(appCode)) {
                    appCode = firstNonBlank(explicitAppCode, springName);
                }
                if (executorPort == null) {
                    executorPort = parsePort(portText);
                }
            }
        }

        if (Strings.isBlank(appCode)) {
            appCode = "demo";
        }
        int port = executorPort != null ? executorPort : 20550;
        String executorUrl = "http://127.0.0.1:" + port;

        return new ProjectMetadata(appCode.trim(), executorUrl);
    }

    static String readNestedValue(String text, String... path) {
        if (Strings.isBlank(text) || path == null || path.length == 0) {
            return null;
        }
        String[] lines = text.split("\\R");
        int targetDepth = path.length;
        int[] targetIndents = new int[targetDepth];
        int matchedDepth = 0;

        for (String rawLine : lines) {
            if (Strings.isBlank(rawLine) || rawLine.trim().startsWith("#")) {
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
        List<Path> files = new ArrayList<Path>();
        collectApplicationYml(projectRoot.resolve("src/main/resources"), files);
        if (Files.isDirectory(projectRoot)) {
            for (Path module : sortedChildDirectories(projectRoot)) {
                if (Files.isRegularFile(module.resolve("pom.xml"))) {
                    collectApplicationYml(module.resolve("src/main/resources"), files);
                }
            }
        }
        Collections.sort(files, new Comparator<Path>() {
            @Override
            public int compare(Path left, Path right) {
                return left.toString().compareTo(right.toString());
            }
        });
        return files;
    }

    private static List<Path> sortedChildDirectories(Path projectRoot) throws IOException {
        List<Path> children = new ArrayList<Path>();
        for (Path child : Files.newDirectoryStream(projectRoot)) {
            if (Files.isDirectory(child)) {
                children.add(child);
            }
        }
        Collections.sort(children, new Comparator<Path>() {
            @Override
            public int compare(Path left, Path right) {
                return left.getFileName().toString().compareTo(right.getFileName().toString());
            }
        });
        return children;
    }

    private static void collectApplicationYml(Path resourcesDir, List<Path> files) throws IOException {
        if (!Files.isDirectory(resourcesDir)) {
            return;
        }
        List<Path> configs = new ArrayList<Path>();
        for (Path file : Files.newDirectoryStream(resourcesDir)) {
            if (Files.isRegularFile(file) && isApplicationConfig(file)) {
                configs.add(file);
            }
        }
        Collections.sort(configs, new Comparator<Path>() {
            @Override
            public int compare(Path left, Path right) {
                return left.getFileName().toString().compareTo(right.getFileName().toString());
            }
        });
        files.addAll(configs);
    }

    private static boolean isApplicationConfig(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.startsWith("application") && (name.endsWith(".yml") || name.endsWith(".yaml"));
    }

    private static int scoreYaml(String explicitAppCode, String springName, String portText) {
        int score = 0;
        if (Strings.isNotBlank(portText)) {
            score += 4;
        }
        if (Strings.isNotBlank(explicitAppCode)) {
            score += 2;
        }
        if (Strings.isNotBlank(springName)) {
            score += 1;
        }
        return score;
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (Strings.isNotBlank(primary)) {
            return primary.trim();
        }
        if (Strings.isNotBlank(fallback)) {
            return fallback.trim();
        }
        return null;
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
        if (Strings.isBlank(value) || "null".equalsIgnoreCase(value)) {
            return null;
        }
        return value.trim();
    }

    private static Integer parsePort(String value) {
        if (Strings.isBlank(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
