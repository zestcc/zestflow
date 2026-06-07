package com.zestflow.devinit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 检测模块内与 ZestFlow 相关的配置缺口（只读，不修改文件）。
 */
public final class ZestFlowConfigInspector {

    private static final Pattern INLINE_ZESTFLOW = Pattern.compile("(?m)^zestflow\\s*:");

    private ZestFlowConfigInspector() {
    }

    public static ModuleConfigGaps inspect(Path projectRoot, Path module) throws IOException {
        Path resources = moduleResources(module, projectRoot);
        ModuleConfigGaps gaps = new ModuleConfigGaps(moduleRelativePrefix(projectRoot, module));
        if (!Files.isDirectory(resources)) {
            gaps.missingApplicationYml = true;
            gaps.missingZestflowConfig = true;
            gaps.missingDatasourceConfig = true;
            return gaps;
        }
        List<Path> configs = listRuntimeConfigs(resources);
        gaps.missingApplicationYml = !Files.isRegularFile(resources.resolve("application.yml"));
        gaps.missingZestflowConfig = !hasZestflowConfig(configs);
        gaps.missingDatasourceConfig = !hasDatasourceConfig(configs)
                && !Files.isRegularFile(resources.resolve("application-local.yml"));
        Path pom = module.resolve("pom.xml");
        gaps.missingStarterDependency = !Files.isRegularFile(pom) || !pomDeclaresStarter(pom);
        return gaps;
    }

    static boolean hasZestflowConfig(List<Path> configs) throws IOException {
        for (Path file : configs) {
            String text = IoUtil.readFile(file);
            if (INLINE_ZESTFLOW.matcher(text).find()) {
                return true;
            }
            if (ProjectMetadataResolver.readNestedValue(text, "zestflow", "executor", "port") != null) {
                return true;
            }
        }
        return false;
    }

    static boolean hasDatasourceConfig(List<Path> configs) throws IOException {
        for (Path file : configs) {
            String lower = IoUtil.readFile(file).toLowerCase(Locale.ROOT);
            if (lower.contains("datasource:") || lower.contains("jdbc:")) {
                return true;
            }
        }
        return false;
    }

    static List<Path> listRuntimeConfigs(Path resources) throws IOException {
        List<Path> files = new ArrayList<Path>();
        if (!Files.isDirectory(resources)) {
            return files;
        }
        for (Path file : Files.newDirectoryStream(resources)) {
            if (!Files.isRegularFile(file)) {
                continue;
            }
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (!name.startsWith("application")) {
                continue;
            }
            if (!name.endsWith(".yml") && !name.endsWith(".yaml")) {
                continue;
            }
            if (name.contains(".example.")) {
                continue;
            }
            files.add(file);
        }
        return files;
    }

    static List<Path> findCandidateModules(Path projectRoot) throws IOException {
        List<Path> modules = ApplicationConfigBootstrap.findModulesWithStarter(projectRoot);
        if (!modules.isEmpty()) {
            return modules;
        }
        List<Path> candidates = new ArrayList<Path>();
        if (Files.isDirectory(projectRoot.resolve("src/main/resources"))) {
            candidates.add(projectRoot);
        }
        if (!Files.isDirectory(projectRoot)) {
            return candidates;
        }
        for (Path child : Files.newDirectoryStream(projectRoot)) {
            if (Files.isDirectory(child) && Files.isDirectory(child.resolve("src/main/resources"))) {
                candidates.add(child);
            }
        }
        return candidates;
    }

    private static Path moduleResources(Path module, Path projectRoot) {
        if (module.equals(projectRoot)) {
            return projectRoot.resolve("src/main/resources");
        }
        return module.resolve("src/main/resources");
    }

    private static String moduleRelativePrefix(Path projectRoot, Path module) {
        if (module.equals(projectRoot)) {
            return "src/main/resources";
        }
        return projectRoot.relativize(module).toString().replace('\\', '/')
                + "/src/main/resources";
    }

    private static boolean pomDeclaresStarter(Path pom) {
        try {
            return IoUtil.readFile(pom).contains("<artifactId>zestflow-starter</artifactId>");
        } catch (IOException e) {
            return false;
        }
    }

    public static final class ModuleConfigGaps {
        public final String resourcesPrefix;
        public boolean missingApplicationYml;
        public boolean missingZestflowConfig;
        public boolean missingDatasourceConfig;
        public boolean missingStarterDependency;

        ModuleConfigGaps(String resourcesPrefix) {
            this.resourcesPrefix = resourcesPrefix;
        }

        public boolean hasAnyGap() {
            return missingApplicationYml || missingZestflowConfig
                    || missingDatasourceConfig || missingStarterDependency;
        }
    }
}
