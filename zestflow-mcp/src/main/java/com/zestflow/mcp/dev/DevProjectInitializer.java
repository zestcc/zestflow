package com.zestflow.mcp.dev;

import com.zestflow.mcp.io.ResourceLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 classpath 内 Dev 模板解压到业务工程根目录（不覆盖已有文件，除非 {@code force}）。
 */
public final class DevProjectInitializer {

    private static final String TEMPLATE_ROOT = "META-INF/zestflow/dev-templates/";

    private DevProjectInitializer() {
    }

    public static DevInitResult initialize(Path projectRoot, DevInitOptions options) throws IOException {
        Map<String, String> vars = buildVariables(projectRoot, options);
        List<String> created = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        writeFromTemplate(
                projectRoot,
                TEMPLATE_ROOT + "rules/project.md.template",
                ".zestflow/rules/project.md",
                vars,
                options.force(),
                created,
                skipped);

        if (options.ides().contains(DevInitOptions.IdeTarget.CURSOR)) {
            writeFromTemplate(
                    projectRoot,
                    TEMPLATE_ROOT + "mcp/cursor.mcp.json.template",
                    ".cursor/mcp.json",
                    vars,
                    options.force(),
                    created,
                    skipped);
        }
        if (options.ides().contains(DevInitOptions.IdeTarget.VSCODE)) {
            writeFromTemplate(
                    projectRoot,
                    TEMPLATE_ROOT + "mcp/vscode.mcp.json.template",
                    ".vscode/mcp.json",
                    vars,
                    options.force(),
                    created,
                    skipped);
        }
        if (options.ides().contains(DevInitOptions.IdeTarget.CLAUDE)) {
            writeFromTemplate(
                    projectRoot,
                    TEMPLATE_ROOT + "mcp/claude-desktop.config.json.template",
                    ".zestflow/mcp/claude-desktop.config.json.example",
                    vars,
                    options.force(),
                    created,
                    skipped);
        }

        Path learningDir = projectRoot.resolve(".zestflow/learning");
        if (!Files.isDirectory(learningDir)) {
            Files.createDirectories(learningDir);
            created.add(".zestflow/learning/");
        }

        if (options.appendGitignore()) {
            appendGitignoreSnippet(projectRoot, created, skipped);
        }

        return new DevInitResult(created, skipped, vars);
    }

    private static Map<String, String> buildVariables(Path projectRoot, DevInitOptions options) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("APP_CODE", options.appCode());
        vars.put("EXECUTOR_URL", options.executorUrl());
        vars.put("BASE_PACKAGE", options.basePackage());
        vars.put("PROJECT_PATH", projectRoot.toAbsolutePath().normalize().toString().replace('\\', '/'));
        return vars;
    }

    private static void writeFromTemplate(
            Path projectRoot,
            String classpathTemplate,
            String relativeTarget,
            Map<String, String> vars,
            boolean force,
            List<String> created,
            List<String> skipped) throws IOException {
        Path target = projectRoot.resolve(relativeTarget).normalize();
        if (!target.startsWith(projectRoot)) {
            throw new IOException("非法目标路径: " + relativeTarget);
        }
        if (Files.exists(target) && !force) {
            skipped.add(relativeTarget);
            return;
        }
        boolean existed = Files.exists(target);
        String template = ResourceLoader.readClasspath(classpathTemplate);
        String content = substitute(template, vars);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
        created.add(existed && force ? relativeTarget + " (overwritten)" : relativeTarget);
    }

    private static void appendGitignoreSnippet(Path projectRoot, List<String> created, List<String> skipped)
            throws IOException {
        Path gitignore = projectRoot.resolve(".gitignore");
        String snippet = ResourceLoader.readClasspath(TEMPLATE_ROOT + "gitignore.zestflow.snippet").trim();
        if (!Files.isRegularFile(gitignore)) {
            Files.writeString(gitignore, snippet + System.lineSeparator(), StandardCharsets.UTF_8);
            created.add(".gitignore");
            return;
        }
        String existing = Files.readString(gitignore, StandardCharsets.UTF_8);
        if (existing.contains(".zestflow/learning/")) {
            skipped.add(".gitignore (already contains zestflow entries)");
            return;
        }
        String merged = existing.endsWith(System.lineSeparator()) || existing.isEmpty()
                ? existing + System.lineSeparator() + snippet + System.lineSeparator()
                : existing + System.lineSeparator() + System.lineSeparator() + snippet + System.lineSeparator();
        Files.writeString(gitignore, merged, StandardCharsets.UTF_8);
        created.add(".gitignore (appended zestflow entries)");
    }

    static String substitute(String template, Map<String, String> vars) {
        String result = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }
}
