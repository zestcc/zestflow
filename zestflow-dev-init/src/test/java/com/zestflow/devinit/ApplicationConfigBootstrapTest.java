package com.zestflow.devinit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationConfigBootstrapTest {

    @TempDir
    Path projectRoot;

    private DevInitOptions options() {
        return new DevInitOptions(
                "demo-app", "http://127.0.0.1:30550", ComponentizationMode.FULL, "component",
                HttpExposureMode.MODE3, EnumSet.of(DevInitOptions.IdeTarget.CURSOR), false, false, true);
    }

    @Test
    void bootstrap_incrementalWhenApplicationYmlExistsWithoutZestflow() throws Exception {
        Path module = projectRoot.resolve("demo-app");
        Files.createDirectories(module.resolve("src/main/resources"));
        Files.write(module.resolve("pom.xml"),
                "<project><dependencies><dependency><artifactId>zestflow-starter</artifactId></dependency></dependencies></project>"
                        .getBytes(StandardCharsets.UTF_8));
        String originalApp = "spring:\n  application:\n    name: demo-app\n  datasource:\n    url: jdbc:mysql://127.0.0.1:3306/demo\n";
        Files.write(module.resolve("src/main/resources/application.yml"), originalApp.getBytes(StandardCharsets.UTF_8));

        ArrayList<String> created = new ArrayList<String>();
        ArrayList<String> skipped = new ArrayList<String>();

        ApplicationConfigBootstrap.bootstrap(
                projectRoot, options(), ApplicationConfigBootstrap.configVariables(options()), created, skipped);

        String appYml = Files.readString(module.resolve("src/main/resources/application.yml"));
        assertTrue(appYml.contains("name: demo-app"));
        assertTrue(appYml.contains("jdbc:mysql"));
        assertTrue(appYml.contains("application-zestflow.yml"));
        assertTrue(appYml.contains(ApplicationYamlAppender.MARKER));
        assertFalse(created.stream().anyMatch(s -> s.equals("demo-app/src/main/resources/application.yml (incremental seed)")));

        Path zestflowYml = module.resolve("src/main/resources/application-zestflow.yml");
        assertTrue(Files.exists(zestflowYml));
        String zestflow = Files.readString(zestflowYml);
        assertTrue(zestflow.contains("zestflow:"));
        assertTrue(zestflow.contains("30550"));
        assertFalse(zestflow.toLowerCase().contains("h2"));
    }

    @Test
    void bootstrap_createsExampleYmlOnlyWhenDatasourceMissing() throws Exception {
        Path module = projectRoot.resolve("demo-app");
        Files.createDirectories(module.resolve("src/main/resources"));
        Files.write(module.resolve("pom.xml"),
                "<project><dependencies><dependency><artifactId>zestflow-starter</artifactId></dependency></dependencies></project>"
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(module.resolve("src/main/resources/application.yml"),
                ("spring:\n  application:\n    name: demo-app\nzestflow:\n  executor:\n    port: 30550\n")
                        .getBytes(StandardCharsets.UTF_8));

        ArrayList<String> created = new ArrayList<String>();
        ArrayList<String> skipped = new ArrayList<String>();

        ApplicationConfigBootstrap.bootstrap(
                projectRoot, options(), ApplicationConfigBootstrap.configVariables(options()), created, skipped);

        assertFalse(Files.exists(module.resolve("src/main/resources/application-local.yml")));
        assertTrue(Files.exists(module.resolve("src/main/resources/application-local.example.yml")));
        String example = Files.readString(module.resolve("src/main/resources/application-local.example.yml"));
        assertTrue(example.contains("jdbc:mysql"));
        assertTrue(example.contains("username: root"));
        assertFalse(example.toLowerCase().contains("jdbc:h2"));
    }

    @Test
    void bootstrap_seedsApplicationYmlAndZestflowWhenAbsent() throws Exception {
        Path module = projectRoot.resolve("demo-app");
        Files.createDirectories(module.resolve("src/main/resources"));
        Files.write(module.resolve("pom.xml"),
                "<dependency><artifactId>zestflow-starter</artifactId>".getBytes(StandardCharsets.UTF_8));

        ArrayList<String> created = new ArrayList<String>();
        ArrayList<String> skipped = new ArrayList<String>();

        ApplicationConfigBootstrap.bootstrap(
                projectRoot, options(), ApplicationConfigBootstrap.configVariables(options()), created, skipped);

        String yml = Files.readString(module.resolve("src/main/resources/application.yml"));
        assertTrue(yml.contains("demo-app"));
        assertTrue(yml.contains("application-zestflow.yml"));
        assertFalse(yml.contains("zestflow:\n  executor:"));

        String zestflow = Files.readString(module.resolve("src/main/resources/application-zestflow.yml"));
        assertTrue(zestflow.contains("30550"));
        assertFalse(yml.toLowerCase().contains("jdbc:h2"));
    }

    @Test
    void bootstrap_createsStarterSnippetWhenDependencyMissing() throws Exception {
        Path module = projectRoot.resolve("demo-app");
        Files.createDirectories(module.resolve("src/main/resources"));
        Files.write(module.resolve("pom.xml"), "<project></project>".getBytes(StandardCharsets.UTF_8));
        Files.write(module.resolve("src/main/resources/application.yml"),
                "zestflow:\n  executor:\n    port: 20550\n".getBytes(StandardCharsets.UTF_8));

        ArrayList<String> created = new ArrayList<String>();
        ArrayList<String> skipped = new ArrayList<String>();

        ApplicationConfigBootstrap.bootstrap(
                projectRoot, options(), ApplicationConfigBootstrap.configVariables(options()), created, skipped);

        Path snippet = projectRoot.resolve(".zestflow/bootstrap/zestflow-starter-dependency.snippet.xml");
        assertTrue(Files.exists(snippet));
        assertTrue(Files.readString(snippet).contains("zestflow-starter"));
    }
}
