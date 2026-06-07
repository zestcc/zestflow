package com.zestflow.mcp.scaffold;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.mcp.io.ResourceLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 元件 Java 脚手架（只返回文本，不写盘；逻辑对齐 Admin AiComponentScaffoldBuilder）。
 */
public class ComponentScaffoldGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern PACKAGE_IN_RULES =
            Pattern.compile("(?:包路径|package)[:：\\s]+([a-zA-Z][\\w.]+)", Pattern.CASE_INSENSITIVE);

    public String scaffold(Path projectRoot, String componentId, String componentType,
                           String groupName, String description, String packageName) throws IOException {
        if (componentId == null || componentId.isBlank()) {
            throw new IllegalArgumentException("componentId 不能为空");
        }
        String type = componentType == null || componentType.isBlank() ? "EXECUTOR" : componentType.toUpperCase(Locale.ROOT);
        String group = groupName == null || groupName.isBlank() ? "generated" : groupName.trim();
        String desc = description == null || description.isBlank() ? "待实现业务逻辑" : description.trim();
        String pkg = resolvePackageName(projectRoot, packageName);
        String className = toClassName(componentId);
        String methodName = toMethodName(componentId);
        String annotation = annotationForType(type);

        String javaCode = buildJavaClass(pkg, className, group, annotation, componentId, methodName, desc);
        String relativePath = toRelativeJavaPath(pkg, className);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("componentId", componentId);
        out.put("componentType", type);
        out.put("packageName", pkg);
        out.put("suggestedRelativePath", relativePath);
        out.put("fullJavaCode", javaCode);
        out.put("checklist", buildChecklist(componentId));
        out.put("note", "请由 Cursor/Claude Apply 落盘；MCP 不提供 write_project_file。");
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(out);
    }

    private String buildJavaClass(String pkg, String className, String group, String annotation,
                                  String componentId, String methodName, String desc) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(";\n\n");
        sb.append("import com.zestflow.executor.annotation.*;\n");
        sb.append("import com.zestflow.executor.context.ChainContext;\n");
        sb.append("import lombok.extern.slf4j.Slf4j;\n");
        sb.append("import org.springframework.stereotype.Component;\n\n");
        sb.append("@Slf4j\n@Component\n");
        sb.append("@ZestComponent(\"").append(group).append("\")\n");
        sb.append("public class ").append(className).append(" {\n\n");
        sb.append("    /**\n     * ").append(desc).append("\n     */\n");
        sb.append("    ").append(annotation).append("(\"").append(componentId).append("\")\n");
        sb.append("    public Object ").append(methodName).append("(ChainContext ctx) {\n");
        sb.append("        log.debug(\"").append(methodName).append(" 开始执行\");\n");
        sb.append("        // TODO: ").append(desc).append("\n");
        sb.append("        Object result = null;\n");
        sb.append("        log.debug(\"").append(methodName).append(" 执行完成\");\n");
        sb.append("        return result;\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    static String resolvePackageName(Path projectRoot, String explicit) throws IOException {
        if (explicit != null && !explicit.isBlank()) {
            return explicit.trim();
        }
        String rules = ResourceLoader.readProjectRules(projectRoot);
        if (!rules.isBlank()) {
            Matcher m = PACKAGE_IN_RULES.matcher(rules);
            if (m.find()) {
                return m.group(1);
            }
        }
        Path javaRoot = projectRoot.resolve("src/main/java");
        if (Files.isDirectory(javaRoot)) {
            try (Stream<Path> stream = Files.walk(javaRoot)) {
                Path sample = stream
                        .filter(p -> p.toString().endsWith(".java"))
                        .filter(p -> p.getFileName().toString().contains("Handler")
                                || p.getFileName().toString().contains("Component"))
                        .findFirst()
                        .orElse(null);
                if (sample != null) {
                    return extractPackage(Files.readString(sample));
                }
            }
        }
        return "com.zestflow.component.generated";
    }

    private static String extractPackage(String javaSource) {
        for (String line : javaSource.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("package ")) {
                return trimmed.substring(8, trimmed.indexOf(';')).trim();
            }
        }
        return "com.zestflow.component.generated";
    }

    private static List<String> buildChecklist(String componentId) {
        List<String> list = new ArrayList<>();
        list.add("由 IDE diff/Apply 保存到 suggestedRelativePath");
        list.add("补全 TODO 业务逻辑");
        list.add("mvn compile / package 并部署");
        list.add("Admin 发布/reload 后确认元件 " + componentId + " 已注册");
        return list;
    }

    static String toRelativeJavaPath(String packageName, String className) {
        return "src/main/java/" + packageName.replace('.', '/') + "/" + className + ".java";
    }

    static String toClassName(String name) {
        if (name == null || name.isBlank()) {
            return "GeneratedComponent";
        }
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1) + "Component";
    }

    static String toMethodName(String name) {
        if (name == null || name.isBlank()) {
            return "execute";
        }
        return name.substring(0, 1).toLowerCase(Locale.ROOT) + name.substring(1);
    }

    static String annotationForType(String type) {
        return switch (type) {
            case "PREDICATE" -> "@ZestPredicate";
            case "SELECTOR" -> "@ZestSelector";
            case "LOADER" -> "@ZestLoader";
            case "PARSER" -> "@ZestParser";
            case "TRANSFORMER" -> "@ZestTransformer";
            case "FILTER" -> "@ZestFilter";
            case "AGGREGATOR" -> "@ZestAggregator";
            case "SPLITTER" -> "@ZestSplitter";
            case "HTTP_CLIENT" -> "@ZestHttpClient";
            case "MQ_PRODUCER" -> "@ZestMqProducer";
            case "CACHE_READER" -> "@ZestCacheReader";
            case "CACHE_WRITER" -> "@ZestCacheWriter";
            case "LOGGER" -> "@ZestLogger";
            case "DELAY" -> "@ZestDelay";
            default -> "@ZestExecute";
        };
    }
}
