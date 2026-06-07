package com.zestflow.devinit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationYamlAppenderTest {

    @TempDir
    Path dir;

    @Test
    void append_addsImportBlock() throws Exception {
        Path appYml = dir.resolve("application.yml");
        Files.write(appYml, "spring:\n  application:\n    name: demo\n".getBytes(StandardCharsets.UTF_8));

        assertTrue(ApplicationYamlAppender.appendZestflowImportIfNeeded(appYml));

        String text = Files.readString(appYml);
        assertTrue(text.contains("application-zestflow.yml"));
        assertTrue(text.contains(ApplicationYamlAppender.MARKER));
        assertTrue(text.startsWith("spring:"));
    }

    @Test
    void append_skipsWhenInlineZestflowPresent() throws Exception {
        Path appYml = dir.resolve("application.yml");
        Files.write(appYml,
                "spring:\n  application:\n    name: demo\nzestflow:\n  executor:\n    port: 20550\n"
                        .getBytes(StandardCharsets.UTF_8));

        assertFalse(ApplicationYamlAppender.appendZestflowImportIfNeeded(appYml));
        assertFalse(Files.readString(appYml).contains(ApplicationYamlAppender.MARKER));
    }

    @Test
    void append_skipsWhenImportAlreadyPresent() throws Exception {
        Path appYml = dir.resolve("application.yml");
        Files.write(appYml,
                "spring:\n  config:\n    import: optional:classpath:application-zestflow.yml\n"
                        .getBytes(StandardCharsets.UTF_8));

        assertFalse(ApplicationYamlAppender.appendZestflowImportIfNeeded(appYml));
    }
}
