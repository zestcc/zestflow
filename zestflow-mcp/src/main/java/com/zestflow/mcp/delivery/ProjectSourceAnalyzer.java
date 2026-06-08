package com.zestflow.mcp.delivery;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 只读扫描业务工程源码（@ZestChain、@ZestExecute、JavaDoc 等）。
 */
final class ProjectSourceAnalyzer {

    private static final Set<String> SKIP_DIRS = Set.of(
            ".git", "target", "node_modules", "dist", "build", ".idea", ".gradle", "out");
    private static final long MAX_FILE_BYTES = 512 * 1024;

    private static final Pattern ZEST_CHAIN_STRING = Pattern.compile(
            "@ZestChain\\s*\\(\\s*(?:value\\s*=\\s*)?\"([^\"]+)\"");
    private static final Pattern ZEST_EXECUTE = Pattern.compile("@ZestExecute\\(\"([^\"]+)\"\\)");

    private ProjectSourceAnalyzer() {
    }

    static Set<String> scanZestChainKeys(Path projectRoot) throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        walkJava(projectRoot, file -> {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            Matcher m = ZEST_CHAIN_STRING.matcher(content);
            while (m.find()) {
                keys.add(m.group(1));
            }
            if (content.contains("@ZestChain(ChainKeys.")) {
                Matcher constRef = Pattern.compile("@ZestChain\\(ChainKeys\\.([A-Z0-9_]+)\\)").matcher(content);
                while (constRef.find()) {
                    keys.add(constRef.group(1).toLowerCase(Locale.ROOT).replace('_', '.'));
                }
            }
        });
        return keys;
    }

    static List<ExecuteMethodInfo> scanExecuteMethods(Path projectRoot) throws IOException {
        List<ExecuteMethodInfo> methods = new ArrayList<>();
        walkJava(projectRoot, file -> {
            if (!file.getFileName().toString().endsWith("Handler.java")
                    && !file.getFileName().toString().endsWith("Component.java")) {
                return;
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String relative = projectRoot.relativize(file).toString().replace('\\', '/');
            for (int i = 0; i < lines.size(); i++) {
                Matcher m = ZEST_EXECUTE.matcher(lines.get(i));
                if (!m.find()) {
                    continue;
                }
                String methodName = m.group(1);
                int start = i;
                int braceDepth = 0;
                boolean started = false;
                int end = i;
                for (int j = i; j < lines.size(); j++) {
                    String line = lines.get(j);
                    for (char c : line.toCharArray()) {
                        if (c == '{') {
                            braceDepth++;
                            started = true;
                        } else if (c == '}') {
                            braceDepth--;
                        }
                    }
                    end = j;
                    if (started && braceDepth == 0) {
                        break;
                    }
                }
                int lineCount = end - start + 1;
                boolean hasJavaDoc = start > 0 && lines.get(start - 1).trim().equals("*/")
                        || (start > 1 && lines.subList(Math.max(0, start - 8), start).stream()
                        .anyMatch(l -> l.trim().startsWith("/**")));
                methods.add(new ExecuteMethodInfo(relative, methodName, lineCount, hasJavaDoc));
            }
        });
        return methods;
    }

    static int countMarkdownPatterns(Path projectRoot) throws IOException {
        Path patternsDir = projectRoot.resolve(".zestflow/patterns");
        if (!Files.isDirectory(patternsDir)) {
            return 0;
        }
        int[] count = {0};
        Files.walkFileTree(patternsDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().endsWith(".md")
                        && !file.getFileName().toString().equalsIgnoreCase("README.md")) {
                    count[0]++;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return count[0];
    }

    private interface FileConsumer {
        void accept(Path file) throws IOException;
    }

    private static void walkJava(Path projectRoot, FileConsumer consumer) throws IOException {
        Files.walkFileTree(projectRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (shouldSkip(projectRoot, dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!file.getFileName().toString().endsWith(".java")) {
                    return FileVisitResult.CONTINUE;
                }
                if (attrs.size() > MAX_FILE_BYTES) {
                    return FileVisitResult.CONTINUE;
                }
                consumer.accept(file);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static boolean shouldSkip(Path projectRoot, Path dir) {
        if (dir.equals(projectRoot)) {
            return false;
        }
        Path rel = projectRoot.relativize(dir);
        for (int i = 0; i < rel.getNameCount(); i++) {
            if (SKIP_DIRS.contains(rel.getName(i).toString())) {
                return true;
            }
        }
        return false;
    }

    record ExecuteMethodInfo(String relativePath, String methodName, int lineCount, boolean hasJavaDoc) {
    }
}
