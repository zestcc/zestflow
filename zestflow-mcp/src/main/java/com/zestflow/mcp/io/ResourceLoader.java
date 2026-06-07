package com.zestflow.mcp.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 读取 JAR 内规范与项目本地文件。
 */
public final class ResourceLoader {

    private ResourceLoader() {
    }

    public static String readClasspath(String classpathLocation) throws IOException {
        String normalized = classpathLocation.startsWith("/") ? classpathLocation : "/" + classpathLocation;
        try (InputStream in = ResourceLoader.class.getResourceAsStream(normalized)) {
            if (in == null) {
                throw new IOException("Classpath resource not found: " + classpathLocation);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static String readProjectFile(Path projectRoot, String relativePath) throws IOException {
        Path resolved = resolveProjectPath(projectRoot, relativePath);
        if (!Files.isRegularFile(resolved)) {
            throw new IOException("文件不存在: " + relativePath);
        }
        long size = Files.size(resolved);
        if (size > 512 * 1024) {
            throw new IOException("文件过大（>512KB）: " + relativePath);
        }
        return Files.readString(resolved, StandardCharsets.UTF_8);
    }

    public static Path resolveProjectPath(Path projectRoot, String relativePath) throws IOException {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IOException("relativePath 不能为空");
        }
        String normalized = relativePath.replace('\\', '/').trim();
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        if (normalized.startsWith("/") || normalized.contains("..")) {
            throw new IOException("禁止访问 project 根目录外的路径: " + relativePath);
        }
        Path resolved = projectRoot.resolve(normalized).normalize();
        if (!resolved.startsWith(projectRoot)) {
            throw new IOException("禁止访问 project 根目录外的路径: " + relativePath);
        }
        return resolved;
    }

    public static String readProjectRules(Path projectRoot) {
        return readProjectRulesFile(projectRoot, ".zestflow/rules/architecture.md")
                + readProjectRulesFile(projectRoot, ".zestflow/rules/project.md");
    }

    private static String readProjectRulesFile(Path projectRoot, String relativePath) {
        if (projectRoot == null) {
            return "";
        }
        Path rulesFile = projectRoot.resolve(relativePath);
        if (!Files.isRegularFile(rulesFile)) {
            return "";
        }
        try {
            String text = Files.readString(rulesFile, StandardCharsets.UTF_8);
            if (text.isBlank()) {
                return "";
            }
            return text.strip() + "\n\n";
        } catch (IOException e) {
            return "";
        }
    }
}
