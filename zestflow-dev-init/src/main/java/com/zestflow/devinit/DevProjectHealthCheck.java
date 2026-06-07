package com.zestflow.devinit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code --init-dev} 后的项目就绪检查（警告，不阻断）。
 */
public final class DevProjectHealthCheck {

    private DevProjectHealthCheck() {
    }

    public static List<String> warnings(Path projectRoot) throws IOException {
        List<String> warnings = new ArrayList<String>();
        if (!hasZestFlowStarterDependency(projectRoot)) {
            warnings.add(
                    "未在业务模块 pom.xml 中发现 zestflow-starter 依赖。\n"
                            + "元件化（@ZestComponent）需要可执行模块引入 starter，否则 MCP list_components / validate_chain 无法工作。\n"
                            + "请在含 *Application 的可执行模块 pom 中添加：\n"
                            + "  <dependency>\n"
                            + "    <groupId>cn.zestflow.www</groupId>\n"
                            + "    <artifactId>zestflow-starter</artifactId>\n"
                            + "  </dependency>");
        } else if (!isZestFlowStarterInstalledLocally()) {
            warnings.add(
                    "pom 已声明 zestflow-starter，但本地 Maven 仓库中找不到该构件（未 install / 未发布）。\n"
                            + "在 zestflow 仓库根目录执行一次：\n"
                            + "  mvn -pl zestflow-starter -am install -DskipTests\n"
                            + "否则业务模块无法编译，Executor 起不来，MCP 工具会全部失败。");
        }
        if (!hasExecutorPortInYaml(projectRoot)) {
            warnings.add(
                    "未在 application*.yml 中发现 zestflow.executor.port。\n"
                            + "可重新执行 init-dev（不加 --no-bootstrap-config）以增量生成 application-zestflow.yml；\n"
                            + "或将 .zestflow/bootstrap/ 下 snippet 手动合并。默认端口 20550；可用 --executor-url 指定。");
        }
        return warnings;
    }

    static boolean hasZestFlowStarterDependency(Path projectRoot) throws IOException {
        for (Path pom : findPomFiles(projectRoot)) {
            if (pomDeclaresZestFlowStarter(pom)) {
                return true;
            }
        }
        return false;
    }

    static boolean isZestFlowStarterInstalledLocally() {
        Path starterDir = Paths.get(System.getProperty("user.home"), ".m2", "repository",
                "cn", "zestflow", "www", "zestflow-starter");
        if (!Files.isDirectory(starterDir)) {
            return false;
        }
        try {
            for (Path versionDir : Files.newDirectoryStream(starterDir)) {
                if (!Files.isDirectory(versionDir)) {
                    continue;
                }
                Path jar = versionDir.resolve("zestflow-starter-" + versionDir.getFileName() + ".jar");
                if (Files.isRegularFile(jar)) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    static boolean hasExecutorPortInYaml(Path projectRoot) throws IOException {
        for (Path yaml : collectApplicationYmlFiles(projectRoot)) {
            String text = IoUtil.readFile(yaml);
            if (ProjectMetadataResolver.readNestedValue(text, "zestflow", "executor", "port") != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean pomDeclaresZestFlowStarter(Path pom) {
        try {
            String text = IoUtil.readFile(pom);
            return text.contains("<artifactId>zestflow-starter</artifactId>");
        } catch (IOException e) {
            return false;
        }
    }

    private static List<Path> findPomFiles(Path projectRoot) throws IOException {
        List<Path> poms = new ArrayList<Path>();
        Path rootPom = projectRoot.resolve("pom.xml");
        if (Files.isRegularFile(rootPom)) {
            poms.add(rootPom);
        }
        if (!Files.isDirectory(projectRoot)) {
            return poms;
        }
        for (Path child : Files.newDirectoryStream(projectRoot)) {
            if (Files.isDirectory(child)) {
                Path modulePom = child.resolve("pom.xml");
                if (Files.isRegularFile(modulePom)) {
                    poms.add(modulePom);
                }
            }
        }
        return poms;
    }

    private static List<Path> collectApplicationYmlFiles(Path projectRoot) throws IOException {
        List<Path> files = new ArrayList<Path>();
        Path rootResources = projectRoot.resolve("src/main/resources");
        if (Files.isDirectory(rootResources)) {
            for (Path file : Files.newDirectoryStream(rootResources)) {
                if (Files.isRegularFile(file) && file.getFileName().toString().startsWith("application")) {
                    files.add(file);
                }
            }
        }
        if (Files.isDirectory(projectRoot)) {
            for (Path module : Files.newDirectoryStream(projectRoot)) {
                if (!Files.isDirectory(module) || !Files.isRegularFile(module.resolve("pom.xml"))) {
                    continue;
                }
                Path resources = module.resolve("src/main/resources");
                if (!Files.isDirectory(resources)) {
                    continue;
                }
                for (Path file : Files.newDirectoryStream(resources)) {
                    if (Files.isRegularFile(file) && file.getFileName().toString().startsWith("application")) {
                        files.add(file);
                    }
                }
            }
        }
        return files;
    }
}
