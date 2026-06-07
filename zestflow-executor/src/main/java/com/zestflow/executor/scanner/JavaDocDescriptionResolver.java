package com.zestflow.executor.scanner;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从源码 JavaDoc 提取元件备注（注解 {@code description} 为空时的回落）。
 */
public final class JavaDocDescriptionResolver {

    private static final Pattern METHOD_DECL = Pattern.compile(
            "(public|protected|private)\\s+[\\w.<>,\\s\\[\\]]+\\s+(\\w+)\\s*\\(");

    private JavaDocDescriptionResolver() {
    }

    /**
     * @param annotationDescription 注解上的 description，非空则直接返回
     */
    public static String resolve(Method method, Class<?> targetClass, String annotationDescription) {
        if (annotationDescription != null && !annotationDescription.isBlank()) {
            return annotationDescription.trim();
        }
        if (method == null || targetClass == null) {
            return "";
        }
        Path source = locateSourceFile(targetClass);
        if (source == null || !Files.isRegularFile(source)) {
            return "";
        }
        try {
            return parseMethodJavaDoc(Files.readString(source, StandardCharsets.UTF_8), method.getName());
        } catch (IOException e) {
            return "";
        }
    }

    static Path locateSourceFile(Class<?> targetClass) {
        String relative = targetClass.getName().replace('.', '/') + ".java";
        List<Path> roots = sourceSearchRoots();
        for (Path root : roots) {
            Path candidate = root.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate.normalize();
            }
            Path underSrc = root.resolve("src/main/java").resolve(relative);
            if (Files.isRegularFile(underSrc)) {
                return underSrc.normalize();
            }
            Path nested = findNestedSource(root, relative);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static Path findNestedSource(Path root, String relative) {
        try {
            if (!Files.isDirectory(root)) {
                return null;
            }
            try (var stream = Files.list(root)) {
                for (Path child : stream.toList()) {
                    if (!Files.isDirectory(child)) {
                        continue;
                    }
                    Path direct = child.resolve("src/main/java").resolve(relative);
                    if (Files.isRegularFile(direct)) {
                        return direct;
                    }
                }
            }
        } catch (IOException ignored) {
            return null;
        }
        return null;
    }

    private static List<Path> sourceSearchRoots() {
        List<Path> roots = new ArrayList<>();
        addRoot(roots, System.getenv("ZESTFLOW_SOURCE_ROOT"));
        addRoot(roots, System.getProperty("zestflow.source.root"));
        addRoot(roots, System.getProperty("user.dir"));
        return roots;
    }

    private static void addRoot(List<Path> roots, String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        Path p = Paths.get(path.trim()).toAbsolutePath().normalize();
        if (!roots.contains(p)) {
            roots.add(p);
        }
    }

    static String parseMethodJavaDoc(String source, String methodName) {
        if (source == null || source.isBlank() || methodName == null || methodName.isBlank()) {
            return "";
        }
        Matcher matcher = METHOD_DECL.matcher(source);
        while (matcher.find()) {
            if (!methodName.equals(matcher.group(2))) {
                continue;
            }
            int methodStart = matcher.start();
            String block = extractJavaDocBlockBefore(source, methodStart);
            if (block == null || block.isBlank()) {
                return "";
            }
            return formatJavaDocBlock(block);
        }
        return "";
    }

    private static String extractJavaDocBlockBefore(String source, int methodStart) {
        int searchFrom = Math.max(0, methodStart - 4000);
        String segment = source.substring(searchFrom, methodStart);
        int end = segment.lastIndexOf("*/");
        if (end < 0) {
            return null;
        }
        int start = segment.lastIndexOf("/**", end);
        if (start < 0) {
            return null;
        }
        return segment.substring(start, end + 2);
    }

    private static String formatJavaDocBlock(String block) {
        String[] lines = block.split("\\R");
        StringBuilder sb = new StringBuilder();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("/**") || line.startsWith("*/")) {
                continue;
            }
            if (line.startsWith("*")) {
                line = line.substring(1).trim();
            }
            if (line.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line);
        }
        return sb.toString().trim();
    }
}
