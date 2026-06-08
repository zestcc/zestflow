package com.zestflow.devinit;

import java.io.IOException;
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
        List<String> created = new ArrayList<String>();
        List<String> skipped = new ArrayList<String>();

        String architectureBody = substitute(
                IoUtil.readClasspath(TEMPLATE_ROOT + "rules/architecture.md.template"), vars);
        vars.put("ARCHITECTURE_BODY", architectureBody);

        writeContent(
                projectRoot,
                ".zestflow/rules/architecture.md",
                architectureBody,
                options.force(),
                created,
                skipped);

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
            writeFromTemplate(
                    projectRoot,
                    TEMPLATE_ROOT + "ide/cursor-rules.md.template",
                    ".cursor/rules/zestflow-architecture.md",
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
            writeFromTemplate(
                    projectRoot,
                    TEMPLATE_ROOT + "ide/copilot-instructions.md.template",
                    ".github/copilot-instructions.md",
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
            writeFromTemplate(
                    projectRoot,
                    TEMPLATE_ROOT + "ide/claude.md.template",
                    "CLAUDE.md",
                    vars,
                    options.force(),
                    created,
                    skipped);
        }
        if (options.ides().contains(DevInitOptions.IdeTarget.CLAUDE_CODE)) {
            writeFromTemplate(
                    projectRoot,
                    TEMPLATE_ROOT + "mcp/claude-code.mcp.json.template",
                    ".mcp.json",
                    vars,
                    options.force(),
                    created,
                    skipped);
        }
        if (options.ides().contains(DevInitOptions.IdeTarget.WINDSURF)) {
            writeFromTemplate(
                    projectRoot,
                    TEMPLATE_ROOT + "mcp/windsurf.mcp_config.json.example.template",
                    ".zestflow/mcp/windsurf.mcp_config.json.example",
                    vars,
                    options.force(),
                    created,
                    skipped);
        }

        writeFromTemplate(
                projectRoot,
                TEMPLATE_ROOT + "mcp/ide-setup.md.template",
                ".zestflow/mcp/README.md",
                vars,
                options.force(),
                created,
                skipped);

        Path learningDir = projectRoot.resolve(".zestflow/learning");
        if (!Files.isDirectory(learningDir)) {
            Files.createDirectories(learningDir);
            created.add(".zestflow/learning/");
        }

        if (options.appendGitignore()) {
            appendGitignoreSnippet(projectRoot, created, skipped);
        }

        Map<String, String> configVars = ApplicationConfigBootstrap.configVariables(options);
        configVars.putAll(vars);
        ApplicationConfigBootstrap.bootstrap(projectRoot, options, configVars, created, skipped);

        List<String> warnings = DevProjectHealthCheck.warnings(projectRoot);
        return new DevInitResult(created, skipped, vars, warnings);
    }

    private static Map<String, String> buildVariables(Path projectRoot, DevInitOptions options) {
        Map<String, String> vars = new LinkedHashMap<String, String>();
        vars.put("APP_CODE", options.appCode());
        vars.put("EXECUTOR_URL", options.executorUrl());
        vars.put("COMPONENTIZATION", options.componentization().cliValue());
        vars.put("COMPONENTIZATION_SECTION", options.componentization().architectureSection());
        vars.put("COMPONENT_PACKAGE", options.componentPackage());
        vars.put("HTTP_MODE", options.httpMode().cliValue());
        vars.put("HTTP_MODE_SECTION", options.httpMode().architectureSection());
        vars.put("CODING_STANDARDS_SECTION", CodingStandards.architectureSection());
        vars.put("PROJECT_PATH", projectRoot.toAbsolutePath().normalize().toString().replace('\\', '/'));
        return vars;
    }

    private static void writeContent(
            Path projectRoot,
            String relativeTarget,
            String content,
            boolean force,
            List<String> created,
            List<String> skipped) throws IOException {
        Path target = projectRoot.resolve(relativeTarget).normalize();
        if (!target.startsWith(projectRoot)) {
            throw new IOException("非法目标路径: " + relativeTarget);
        }
        if (ProtectedProjectPaths.isProtected(projectRoot, target)) {
            skipped.add(relativeTarget + " (protected: 禁止覆盖 application/pom 等)");
            return;
        }
        if (Files.exists(target) && !force) {
            skipped.add(relativeTarget);
            return;
        }
        boolean existed = Files.exists(target);
        Files.createDirectories(target.getParent());
        IoUtil.writeFile(target, content);
        created.add(existed && force ? relativeTarget + " (overwritten)" : relativeTarget);
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
        if (ProtectedProjectPaths.isProtected(projectRoot, target)) {
            skipped.add(relativeTarget + " (protected: 禁止覆盖 application/pom 等)");
            return;
        }
        if (Files.exists(target) && !force) {
            skipped.add(relativeTarget);
            return;
        }
        boolean existed = Files.exists(target);
        String template = IoUtil.readClasspath(classpathTemplate);
        String content = substitute(template, vars);
        Files.createDirectories(target.getParent());
        IoUtil.writeFile(target, content);
        created.add(existed && force ? relativeTarget + " (overwritten)" : relativeTarget);
    }

    private static void appendGitignoreSnippet(Path projectRoot, List<String> created, List<String> skipped)
            throws IOException {
        Path gitignore = projectRoot.resolve(".gitignore");
        String snippet = IoUtil.readClasspath(TEMPLATE_ROOT + "gitignore.zestflow.snippet").trim();
        if (!Files.isRegularFile(gitignore)) {
            IoUtil.writeFile(gitignore, snippet + System.lineSeparator());
            created.add(".gitignore");
            return;
        }
        String existing = IoUtil.readFile(gitignore);
        if (existing.contains(".zestflow/learning/")) {
            skipped.add(".gitignore (already contains zestflow entries)");
            return;
        }
        String merged = existing.endsWith(System.lineSeparator()) || existing.isEmpty()
                ? existing + System.lineSeparator() + snippet + System.lineSeparator()
                : existing + System.lineSeparator() + System.lineSeparator() + snippet + System.lineSeparator();
        IoUtil.writeFile(gitignore, merged);
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
