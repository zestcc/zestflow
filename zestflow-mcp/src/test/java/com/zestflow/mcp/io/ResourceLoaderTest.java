package com.zestflow.mcp.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void readProjectFile_blocksPathTraversal() {
        assertThrows(IOException.class, () ->
                ResourceLoader.readProjectFile(tempDir, "../outside.txt"));
    }

    @Test
    void readProjectFile_readsWithinProject() throws Exception {
        Path file = tempDir.resolve("src/demo.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "hello");
        assertEquals("hello", ResourceLoader.readProjectFile(tempDir, "src/demo.txt"));
    }

    @Test
    void readClasspath_loadsComponentRule() throws Exception {
        String text = ResourceLoader.readClasspath("zestflow/rules/component-development.md");
        assertTrue(text.contains("@ZestComponent"));
    }
}
