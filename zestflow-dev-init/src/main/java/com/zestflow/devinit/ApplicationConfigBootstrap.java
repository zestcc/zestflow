package com.zestflow.devinit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 按缺口增量补齐 ZestFlow 相关配置：缺什么补什么，**绝不覆盖**已有文件内容。
 */
public final class ApplicationConfigBootstrap {

    private static final String TEMPLATE_ROOT = "META-INF/zestflow/dev-templates/config/";
    private static final String BOOTSTRAP_ROOT = "META-INF/zestflow/dev-templates/bootstrap/";

    private ApplicationConfigBootstrap() {
    }

    public static void bootstrap(
            Path projectRoot,
            DevInitOptions options,
            Map<String, String> vars,
            List<String> created,
            List<String> skipped) throws IOException {
        if (!options.bootstrapConfig()) {
            return;
        }
        for (Path module : ZestFlowConfigInspector.findCandidateModules(projectRoot)) {
            ZestFlowConfigInspector.ModuleConfigGaps gaps =
                    ZestFlowConfigInspector.inspect(projectRoot, module);
            if (!gaps.hasAnyGap()) {
                skipped.add(gaps.resourcesPrefix + " (zestflow config complete)");
                continue;
            }
            fillGaps(projectRoot, gaps, vars, created, skipped);
        }
        seedStarterSnippetIfNeeded(projectRoot, vars, created, skipped);
    }

    private static void fillGaps(
            Path projectRoot,
            ZestFlowConfigInspector.ModuleConfigGaps gaps,
            Map<String, String> vars,
            List<String> created,
            List<String> skipped) throws IOException {
        String prefix = gaps.resourcesPrefix;

        String zestflowRel = prefix + "/application-zestflow.yml";

        if (gaps.missingApplicationYml) {
            seedNewFile(projectRoot, prefix + "/application.yml",
                    TEMPLATE_ROOT + "application.yml.template", vars, created);
            seedNewFile(projectRoot, zestflowRel,
                    TEMPLATE_ROOT + "application-zestflow.yml.template", vars, created);
        } else if (gaps.missingZestflowConfig) {
            if (seedNewFile(projectRoot, zestflowRel,
                    TEMPLATE_ROOT + "application-zestflow.yml.template", vars, created)) {
                Path appYml = projectRoot.resolve(prefix + "/application.yml").normalize();
                if (ApplicationYamlAppender.appendZestflowImportIfNeeded(appYml)) {
                    created.add(prefix + "/application.yml (appended zestflow import only)");
                } else {
                    skipped.add(prefix + "/application.yml (zestflow inline or import already present)");
                }
            }
        } else {
            skipped.add(prefix + "/application-zestflow.yml (zestflow config already present)");
        }

        if (gaps.missingDatasourceConfig) {
            seedNewFile(projectRoot, prefix + "/application-local.example.yml",
                    TEMPLATE_ROOT + "application-local.example.yml.template", vars, created);
        } else {
            skipped.add(prefix + "/application-local.example.yml (datasource already configured)");
        }
    }

    private static void seedStarterSnippetIfNeeded(
            Path projectRoot,
            Map<String, String> vars,
            List<String> created,
            List<String> skipped) throws IOException {
        boolean anyMissing = false;
        for (Path module : ZestFlowConfigInspector.findCandidateModules(projectRoot)) {
            if (ZestFlowConfigInspector.inspect(projectRoot, module).missingStarterDependency) {
                anyMissing = true;
                break;
            }
        }
        if (!anyMissing) {
            return;
        }
        String rel = ".zestflow/bootstrap/zestflow-starter-dependency.snippet.xml";
        Path target = projectRoot.resolve(rel).normalize();
        if (Files.exists(target)) {
            skipped.add(rel + " (already exists)");
            return;
        }
        String template = IoUtil.readClasspath(BOOTSTRAP_ROOT + "zestflow-starter-dependency.snippet.xml");
        Files.createDirectories(target.getParent());
        IoUtil.writeFile(target, DevProjectInitializer.substitute(template, vars));
        created.add(rel + " (merge into module pom manually)");
    }

    private static boolean seedNewFile(
            Path projectRoot,
            String relativeTarget,
            String classpathTemplate,
            Map<String, String> vars,
            List<String> created) throws IOException {
        Path target = projectRoot.resolve(relativeTarget).normalize();
        if (!target.startsWith(projectRoot.toAbsolutePath().normalize())) {
            throw new IOException("非法目标路径: " + relativeTarget);
        }
        if (Files.exists(target)) {
            return false;
        }
        String template = IoUtil.readClasspath(classpathTemplate);
        String content = DevProjectInitializer.substitute(template, vars);
        Files.createDirectories(target.getParent());
        IoUtil.writeFile(target, content);
        created.add(relativeTarget + " (incremental seed)");
        return true;
    }

    static List<Path> findModulesWithStarter(Path projectRoot) throws IOException {
        List<Path> modules = new ArrayList<Path>();
        if (!Files.isDirectory(projectRoot)) {
            return modules;
        }
        Path rootPom = projectRoot.resolve("pom.xml");
        if (Files.isRegularFile(rootPom) && pomDeclaresStarter(rootPom)) {
            modules.add(projectRoot);
        }
        for (Path child : Files.newDirectoryStream(projectRoot)) {
            if (!Files.isDirectory(child)) {
                continue;
            }
            Path pom = child.resolve("pom.xml");
            if (Files.isRegularFile(pom) && pomDeclaresStarter(pom)) {
                modules.add(child);
            }
        }
        return modules;
    }

    private static boolean pomDeclaresStarter(Path pom) {
        try {
            return IoUtil.readFile(pom).contains("<artifactId>zestflow-starter</artifactId>");
        } catch (IOException e) {
            return false;
        }
    }

    static Map<String, String> configVariables(DevInitOptions options) {
        Map<String, String> vars = new LinkedHashMap<String, String>();
        vars.put("APP_CODE", options.appCode());
        String port = "20550";
        String url = options.executorUrl();
        if (Strings.isNotBlank(url) && url.contains(":")) {
            int lastColon = url.lastIndexOf(':');
            String tail = url.substring(lastColon + 1);
            if (tail.matches("\\d+")) {
                port = tail;
            }
        }
        vars.put("EXECUTOR_PORT", port);
        String dbBase = toDbName(options.appCode());
        vars.put("DB_BUSINESS", dbBase + "_business");
        vars.put("DB_LOG", dbBase + "_log");
        return vars;
    }

    static String toDbName(String appCode) {
        if (Strings.isBlank(appCode)) {
            return "zestflow_app";
        }
        return appCode.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
