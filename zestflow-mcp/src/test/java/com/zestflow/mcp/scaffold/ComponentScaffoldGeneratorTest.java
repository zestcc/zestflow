package com.zestflow.mcp.scaffold;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentScaffoldGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    void scaffold_includesJavaAndSuggestedPath() throws Exception {
        String json = new ComponentScaffoldGenerator().scaffold(
                tempDir, "deductStock", "EXECUTOR", "order", "扣库存", null);
        assertTrue(json.contains("deductStock"));
        assertTrue(json.contains("@ZestExecute"));
        assertTrue(json.contains("Repo"));
        assertTrue(json.contains("Command"));
        assertTrue(json.contains("@param command"));
        assertTrue(json.contains("suggestedRelativePath"));
        assertTrue(json.contains("Cursor/Claude Apply"));
    }
}
