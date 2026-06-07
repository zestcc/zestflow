package com.zestflow.mcp.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectSourceSearcherTest {

    @Test
    void matchesGlob_javaSuffix() {
        assertTrue(ProjectSourceSearcher.matchesGlob("src/main/java/Foo.java", "**/*.java"));
        assertFalse(ProjectSourceSearcher.matchesGlob("src/main/resources/a.xml", "**/*.java"));
    }
}
